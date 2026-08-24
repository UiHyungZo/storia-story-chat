describe("API_BASE_URL", () => {
  const originalEnv = process.env.EXPO_PUBLIC_API_BASE_URL;

  afterEach(() => {
    process.env.EXPO_PUBLIC_API_BASE_URL = originalEnv;
    jest.resetModules();
    jest.dontMock("react-native");
  });

  it("uses 10.0.2.2 on Android (emulator loopback to the host machine)", () => {
    delete process.env.EXPO_PUBLIC_API_BASE_URL;
    jest.resetModules();
    jest.doMock("react-native", () => ({ Platform: { OS: "android" } }));

    const { API_BASE_URL } = require("../config");

    expect(API_BASE_URL).toBe("http://10.0.2.2:8080");
  });

  it("uses localhost on iOS (simulator shares the host's loopback)", () => {
    delete process.env.EXPO_PUBLIC_API_BASE_URL;
    jest.resetModules();
    jest.doMock("react-native", () => ({ Platform: { OS: "ios" } }));

    const { API_BASE_URL } = require("../config");

    expect(API_BASE_URL).toBe("http://localhost:8080");
  });

  it("prefers EXPO_PUBLIC_API_BASE_URL when set (physical device / LAN IP)", () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://192.168.1.50:8080";
    jest.resetModules();
    jest.doMock("react-native", () => ({ Platform: { OS: "ios" } }));

    const { API_BASE_URL } = require("../config");

    expect(API_BASE_URL).toBe("http://192.168.1.50:8080");
  });
});
