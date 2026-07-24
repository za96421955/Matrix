#!/usr/bin/env python3
"""
matrix-local WebUI 代理服务器
- 对 /v1/* 路径，反向代理到后端服务 (localhost:10906)
- 对其他路径，返回 webui 目录下的静态文件
- 监听端口 10908
"""

import http.server
import urllib.request
import urllib.error
import os
import sys
import signal

BACKEND_HOST = os.environ.get("MATRIX_BACKEND_HOST", "localhost")
BACKEND_PORT = int(os.environ.get("MATRIX_BACKEND_PORT", "10906"))
WEBUI_DIR = os.environ.get("MATRIX_WEBUI_DIR", "")
LISTEN_PORT = int(os.environ.get("MATRIX_WEBUI_PORT", "10908"))


class ProxyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/v1/"):
            self.proxy_request("GET")
        else:
            super().do_GET()

    def do_POST(self):
        if self.path.startswith("/v1/"):
            self.proxy_request("POST")
        else:
            self.send_error(405, "Method Not Allowed")

    def do_PUT(self):
        if self.path.startswith("/v1/"):
            self.proxy_request("PUT")
        else:
            self.send_error(405, "Method Not Allowed")

    def do_DELETE(self):
        if self.path.startswith("/v1/"):
            self.proxy_request("DELETE")
        else:
            self.send_error(405, "Method Not Allowed")

    def do_PATCH(self):
        if self.path.startswith("/v1/"):
            self.proxy_request("PATCH")
        else:
            self.send_error(405, "Method Not Allowed")

    def do_OPTIONS(self):
        if self.path.startswith("/v1/"):
            self.proxy_request("OPTIONS")
        else:
            self.send_error(405, "Method Not Allowed")

    def proxy_request(self, method):
        backend_url = f"http://{BACKEND_HOST}:{BACKEND_PORT}{self.path}"
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else None

        req = urllib.request.Request(
            backend_url,
            data=body,
            headers={
                "Content-Type": self.headers.get("Content-Type", ""),
                "Accept": self.headers.get("Accept", ""),
                "Authorization": self.headers.get("Authorization", ""),
                "Cookie": self.headers.get("Cookie", ""),
                "X-Requested-With": self.headers.get("X-Requested-With", ""),
            },
            method=method,
        )

        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                self.send_response(response.status)
                # 透传关键响应头
                for header_name in ["Content-Type", "Content-Length", "Set-Cookie", "Cache-Control"]:
                    header_value = response.headers.get(header_name)
                    if header_value:
                        self.send_header(header_name, header_value)
                self.end_headers()
                self.wfile.write(response.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for header_name in ["Content-Type", "Content-Length", "Set-Cookie"]:
                header_value = e.headers.get(header_name)
                if header_value:
                    self.send_header(header_name, header_value)
            self.end_headers()
            self.wfile.write(e.read())
        except urllib.error.URLError as e:
            self.send_error(502, f"Bad Gateway: {e.reason}")
        except Exception as e:
            self.send_error(500, f"Internal Server Error: {str(e)}")

    def log_message(self, format, *args):
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), format % args))


if __name__ == "__main__":
    # 设置工作目录为 webui 目录
    if WEBUI_DIR and os.path.isdir(WEBUI_DIR):
        os.chdir(WEBUI_DIR)
        print(f"[INFO] WebUI 目录: {WEBUI_DIR}")
    else:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.dirname(script_dir)
        auto_webui_dir = os.path.join(project_root, "webui")
        if os.path.isdir(auto_webui_dir):
            os.chdir(auto_webui_dir)
            print(f"[INFO] 自动检测 WebUI 目录: {auto_webui_dir}")
        else:
            print(f"[WARN] WebUI 目录未找到，使用当前目录")
            print(f"[WARN] 可设置环境变量 MATRIX_WEBUI_DIR 指定目录")

    print(f"[INFO] 后端代理地址: http://{BACKEND_HOST}:{BACKEND_PORT}")
    print(f"[INFO] 监听端口: {LISTEN_PORT}")

    server = http.server.HTTPServer(("127.0.0.1", LISTEN_PORT), ProxyHTTPRequestHandler)
    print(f"[INFO] 代理服务器启动成功: http://localhost:{LISTEN_PORT}")

    def shutdown(signum, frame):
        print("\n[INFO] 收到关闭信号，正在停止服务器...")
        server.shutdown()
        sys.exit(0)

    # 注册信号处理，Windows 上不存在 SIGTERM，需要条件判断
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        print("[INFO] 服务器已关闭")