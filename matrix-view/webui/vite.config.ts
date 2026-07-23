import {defineConfig} from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"
import path from "path"

export default defineConfig(({mode}) => {
    return {
        base: '/',
        plugins: [tailwindcss(), react()],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src"),
            },
        },
        server: {
            port: 10908,
            proxy: {
                ...({
                    "/v1": {
                        target: "http://localhost:10906",
                        changeOrigin: true,
                    }
                }),
            }
        }
    }
})
