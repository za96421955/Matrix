package com.matrix.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @description HTTP工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
public class HttpClient {

    public static final String POST = "post";
    public static final String GET = "get";
    public static final String PUT = "put";
    public static final String DELETE = "delete";

    private final CloseableHttpClient client;
    private final String url;

    private HttpRequestBase request;
    private String body;
    private int status;
    private String result;

    private HttpClient(String url) {
        this.client = HttpClients.createDefault();
        this.url = url;
    }

    private HttpClient(String url, String tls) {
        CloseableHttpClient client;
        try {
            client = HttpClients.custom()
                    .setSSLContext(SSLContextBuilder.create()
                            .setProtocol(tls)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("HttpClient 初始化异常: {}", e.getMessage(), e);
            client = HttpClients.createDefault();
//            throw new RuntimeException(e);
        }
        this.client = client;
        this.url = url;
    }

    /**
     * @description HttpPost 请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static HttpClient post(String url) {
        return new HttpClient(url).request(new HttpPost(url));
    }

    /**
     * @description HttpPost 长文请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static HttpClient postTLS(String url) {
        return new HttpClient(url, "TLSv1.2").request(new HttpPost(url));
    }

    /**
     * @description HttpGet 请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static HttpClient get(String url) {
        return new HttpClient(url).request(new HttpGet(url));
    }

    /**
     * @description HttpPut 请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static HttpClient put(String url) {
        return new HttpClient(url).request(new HttpPut(url));
    }

    /**
     * @description HttpDelete 请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static HttpClient delete(String url) {
        return new HttpClient(url).request(new HttpDelete(url));
    }

    /**
     * @description 设置请求对象
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private HttpClient request(HttpRequestBase request) {
        this.request = request;
        this.request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        this.request.addHeader(HttpHeaders.ACCEPT, ContentType.WILDCARD.getMimeType());
        return this;
    }

    /**
     * @description 设置请求头
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public HttpClient header(String name, String value) {
        request.addHeader(name, value);
        return this;
    }

    /** contentType操作 */
    public HttpClient contentType(String value) {
        this.header(HttpHeaders.CONTENT_TYPE, value);
        return this;
    }

    /** accept操作 */
    public HttpClient accept(String value) {
        this.header(HttpHeaders.ACCEPT, value);
        return this;
    }

    /** authorization操作 */
    public HttpClient authorization(String value) {
        this.header(HttpHeaders.AUTHORIZATION, "Bearer " + value);
        return this;
    }

    /**
     * @description 设置请求体
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public HttpClient body(String body) {
        if (request instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(body, ContentType.getByMimeType(
                    request.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())));
        }
        this.body = body;
        return this;
    }

    /**
     * @description 执行请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private HttpClient execute() {
        try (CloseableHttpResponse response = client.execute(request)) {
            status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity);
            log.debug("[Http请求] url={}, body={}, response={}, status={}, 请求完成",
                    url, body, result, status);
        } catch (IOException e) {
            log.error("[Http请求] url={}, body={}, 请求异常: {}",
                    url, body, e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * @description 获取响应状态
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public int asStatus() {
        if (status > 0) {
            return status;
        }
        return this.execute().status;
    }

    /**
     * @description 获取字符串结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String asString() {
        if (StringUtils.isNotBlank(result)) {
            return result;
        }
        return this.execute().result;
    }

    /**
     * @description 表单请求
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String asFromData() {
        return this.header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType())
                .asString();
    }

//    /**
//     * @description 获取响应流
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    public InputStream asStream() {
//        /**
//         * setConnectTimeout：设置连接超时时间，单位毫秒。
//         * setConnectionRequestTimeout：设置从connect Manager(连接池)获取Connection
//         * 超时时间，单位毫秒。这个属性是新加的属性，因为目前版本是可以共享连接池的。
//         * setSocketTimeout：请求获取数据的超时时间(即响应时间)，单位毫秒。 如果访问一个接口，多少时间内无法返回数据，就直接放弃此次调用。
//         */
//        RequestConfig requestConfig = RequestConfig.custom()
//                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT)
//                .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT)
//                .build();
//        httpPost.setConfig(requestConfig);
//    }

    /**
     * @description 流处理
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public interface StreamHandle {
        boolean handle(String output);
    }

    /**
     * @description 响应流处理
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public HttpClient asStream(StreamHandle handle) {
//        this.header("Accept-Encoding", "gzip, deflate, br");
//        this.header("Connection", "keep-alive");
        try (
                CloseableHttpResponse response = client.execute(request);
                InputStreamReader reader = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(reader)
        ) {
            status = response.getStatusLine().getStatusCode();
//            if (status < 200 || status >= 300) {
//                log.error("[Http请求] 请求失败，状态码: {}", status);
//                return this;
//            }
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (!handle.handle(line)) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("[Http请求] 异常: {}", e.getMessage(), e);
//            throw new RuntimeException(e);
        }
        return this;
    }

//    /**
//     * @description 获取客户端真实IP
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//	public static String getRemoteAddr(HttpServletRequest request) {
//		if (request == null) {
//		    return null;
//        }
//		String clientIp;
//		// X-Forwarded-For
//		String xff = request.getHeader("X-Forwarded-For");
//        clientIp = (xff == null ? request.getHeader("x-forwarded-for") : xff);
//		// Proxy-Client-IP
//        if (StringUtils.isBlank(clientIp)) {
//			String pci = request.getHeader("Proxy-Client-IP");
//            clientIp = (pci == null ? request.getHeader("proxy-client-ip") : pci);
//		}
//		// WL-Proxy-Client-IP
//        if (StringUtils.isBlank(clientIp)) {
//			String wpci = request.getHeader("WL-Proxy-Client-IP");
//            clientIp = (wpci == null ? request.getHeader("wl-proxy-client-ip") : wpci);
//		}
//		// RemoteAddr
//        if (StringUtils.isBlank(clientIp)) {
//            clientIp = request.getRemoteAddr();
//		}
//		return clientIp.split(",")[0].trim();
//	}
//
//    /**
//     * @description 提取请求中的文件内容
//     * <p> 读取HttpServletRequest上传的文件，调用StringExtractor提取内容 </p>
//     *
//     * @author 陈晨
//     */
//    public static UploadFile getInputStream(HttpServletRequest request) {
//        if (null == request || !ServletFileUpload.isMultipartContent(request)) {
//            return null;
//        }
//        try {
//            for (Part part : request.getParts()) {
//                if (null == part.getContentType() || part.getSize() <= 0) {
//                    continue;
//                }
//                return UploadFile.builder()
//                        .fileName(part.getSubmittedFileName())
//                        .inputStream(part.getInputStream())
//                        .build();
//            }
//        } catch (Exception e) {
//            log.error("文件上传处理异常: {}", e.getMessage(), e);
//        }
//        return null;
//    }
//
//    @Data
//    @Builder
//    public static class UploadFile {
//        private String fileName;
//        private InputStream inputStream;
//
//    }

}


