const getIdTokenClientMock = jest.fn();
const getRequestHeadersMock = jest.fn();

jest.mock("google-auth-library", () => ({
  GoogleAuth: jest.fn().mockImplementation(() => ({
    getIdTokenClient: getIdTokenClientMock,
  })),
}));

import firebaseFunctionsTest from "firebase-functions-test";
import { scoreRiskWithVertex } from "../scoreRiskWithVertex";

describe("scoreRiskWithVertex", () => {
  const testEnv = firebaseFunctionsTest();
  const wrapped = testEnv.wrap(scoreRiskWithVertex);
  const originalEnv = { ...process.env };
  const fetchMock = jest.fn();

  function authContext() {
    return {
      auth: {
        uid: "user-1",
        token: {},
      },
    };
  }

  beforeEach(() => {
    jest.clearAllMocks();
    process.env = { ...originalEnv };

    delete process.env.VERTEX_INFERENCE_URL;
    delete process.env.VERTEX_AUDIENCE;
    delete process.env.VERTEX_ALLOW_FALLBACK;
    delete process.env.VERTEX_TIMEOUT_MS;

    (global as unknown as { fetch: unknown }).fetch = fetchMock;

    getRequestHeadersMock.mockResolvedValue({
      Authorization: "Bearer fake-token",
    });
    getIdTokenClientMock.mockResolvedValue({
      getRequestHeaders: getRequestHeadersMock,
    });
  });

  afterAll(() => {
    process.env = originalEnv;
    testEnv.cleanup();
  });

  test("rejects unauthenticated requests", async () => {
    await expect(wrapped({ bpSystolic: 140 })).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  test("returns local fallback when endpoint is not configured", async () => {
    const result = await wrapped(
      {
        bpSystolic: 180,
        glucose: 300,
      },
      authContext()
    );

    expect(result).toMatchObject({
      provider: "local_fallback",
      fallbackUsed: true,
    });
    expect((result as { reason: string }).reason).toContain("Vertex endpoint is not configured");
    expect((result as { score: number }).score).toBeGreaterThanOrEqual(60);
  });

  test("fails with failed-precondition when endpoint is missing and fallback disabled", async () => {
    process.env.VERTEX_ALLOW_FALLBACK = "false";

    await expect(wrapped({ bpSystolic: 140 }, authContext())).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });

  test("returns normalized vertex response on success", async () => {
    process.env.VERTEX_INFERENCE_URL = "https://vertex-inference";
    process.env.VERTEX_AUDIENCE = "https://vertex-audience";

    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({
          prediction: {
            risk: "high",
            reason: "model output",
            score: 88,
          },
        }),
    });

    const result = await wrapped(
      {
        bpSystolic: 165,
        adherence: 70,
      },
      authContext()
    );

    expect(result).toMatchObject({
      provider: "vertex",
      risk: "HIGH",
      reason: "model output",
      score: 88,
      fallbackUsed: false,
    });

    expect(getIdTokenClientMock).toHaveBeenCalledWith("https://vertex-audience");
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [url, options] = fetchMock.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe("https://vertex-inference");

    const headers = options.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer fake-token");

    const body = JSON.parse(String(options.body));
    expect(body.userId).toBe("user-1");
    expect(body.features.adherence).toBe(70);
  });

  test("returns fallback when vertex call fails and fallback is enabled", async () => {
    process.env.VERTEX_INFERENCE_URL = "https://vertex-inference";
    process.env.VERTEX_AUDIENCE = "https://vertex-audience";
    process.env.VERTEX_ALLOW_FALLBACK = "true";

    fetchMock.mockRejectedValue(new Error("network down"));

    const result = await wrapped(
      {
        bpSystolic: 180,
      },
      authContext()
    );

    expect(result).toMatchObject({
      provider: "local_fallback",
      fallbackUsed: true,
    });
    expect((result as { reason: string }).reason).toContain("Vertex call failed; using local fallback");
  });

  test("throws unavailable when vertex call fails and fallback is disabled", async () => {
    process.env.VERTEX_INFERENCE_URL = "https://vertex-inference";
    process.env.VERTEX_AUDIENCE = "https://vertex-audience";
    process.env.VERTEX_ALLOW_FALLBACK = "false";

    fetchMock.mockRejectedValue(new Error("network down"));

    await expect(wrapped({ bpSystolic: 180 }, authContext())).rejects.toMatchObject({
      code: "unavailable",
    });
  });
});
