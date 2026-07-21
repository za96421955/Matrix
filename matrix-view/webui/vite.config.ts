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
            port: 11018,
            proxy: {
                ...({
                    "/v1": {
                        target: "http://localhost:10626",
                        changeOrigin: true,
                    }
                }),
            }
        }
    }
})
