const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const defaultConfig = getDefaultConfig(__dirname);

const config = {
  resolver: {
    extraNodeModules: {
      '@': `${__dirname}/src`,
    },
  },
  watchFolders: [__dirname],
};

module.exports = mergeConfig(defaultConfig, config);
