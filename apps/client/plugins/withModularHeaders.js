const { withPodfile } = require("@expo/config-plugins");
const { mergeContents } = require("@expo/config-plugins/build/utils/generateCode");

/**
 * Firebase's GoogleUtilities pod doesn't define Swift modules, which pod
 * install rejects when building with static frameworks (react-native-webrtc
 * requires `use_frameworks! :linkage => :static`, set by
 * @config-plugins/react-native-webrtc). `use_modular_headers!` opts every
 * pod into generating a module map, which is CocoaPods' documented fix.
 */
function withModularHeaders(config) {
  return withPodfile(config, (config) => {
    config.modResults.contents = mergeContents({
      src: config.modResults.contents,
      newSrc: "use_modular_headers!",
      tag: "withModularHeaders",
      anchor: /use_frameworks!/,
      offset: 1,
      comment: "#",
    }).contents;
    return config;
  });
}

module.exports = withModularHeaders;
