const { defineConfig } = require("@vue/cli-service");
const path = require("path");
const MonacoWebpackPlugin = require("monaco-editor-webpack-plugin");

module.exports = {
  devServer: {
    port: 8080,
    proxy: {
      "/api": {
        target: "http://localhost:8101",
        changeOrigin: true,
        pathRewrite: {
          "^/api": "/api", // 实际不重写，保持原路径
        },
      },
    },
  },
  configureWebpack: {
    resolve: {
      alias: {
        "@generated": path.resolve(__dirname, "src/generated"),
        "@": path.resolve(__dirname, "src"),
      },
    },
    plugins: [
      new MonacoWebpackPlugin({
        // 需要的语言，按需添加，减少打包体积
        languages: ["json", "javascript", "typescript", "html", "css"],
      }),
    ],
  },
};
