config.devServer = {
    ...config.devServer,
    host: "local-ip",
    allowedHosts: "all",
    historyApiFallback: true,
};
