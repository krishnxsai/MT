import firebaseFunctionsTest from "firebase-functions-test";
import { createRazorpayOrder } from "../createRazorpayOrder";

describe("createRazorpayOrder", () => {
  const testEnv = firebaseFunctionsTest();
  const wrapped = testEnv.wrap(createRazorpayOrder);
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
    delete process.env.RAZORPAY_KEY_ID;
    delete process.env.RAZORPAY_KEY_SECRET;
    (global as unknown as { fetch: unknown }).fetch = fetchMock;
  });

  afterAll(() => {
    process.env = originalEnv;
    testEnv.cleanup();
  });

  test("rejects unauthenticated requests", async () => {
    await expect(wrapped({ amount: 500 })).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  test("rejects invalid amount", async () => {
    await expect(wrapped({ amount: 10.5 }, authContext())).rejects.toMatchObject({
      code: "invalid-argument",
    });

    await expect(wrapped({ amount: 0 }, authContext())).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  test("rejects non-INR currency", async () => {
    await expect(
      wrapped({ amount: 500, currency: "USD" }, authContext())
    ).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  test("fails when credentials are missing", async () => {
    await expect(wrapped({ amount: 500 }, authContext())).rejects.toMatchObject({
      code: "internal",
    });
  });

  test("creates order successfully with Razorpay API", async () => {
    process.env.RAZORPAY_KEY_ID = "rzp_key";
    process.env.RAZORPAY_KEY_SECRET = "rzp_secret";

    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({
          id: "order_123",
          amount: 500,
          currency: "INR",
          receipt: "receipt_1",
          status: "created",
        }),
    });

    const result = await wrapped(
      {
        amount: 500,
        meditrackOrderId: "mt_order_1",
        receipt: "receipt_1",
      },
      authContext()
    );

    expect(result).toMatchObject({
      orderId: "order_123",
      amount: 500,
      currency: "INR",
      receipt: "receipt_1",
      status: "created",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [url, options] = fetchMock.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe("https://api.razorpay.com/v1/orders");
    expect(options.method).toBe("POST");

    const headers = options.headers as Record<string, string>;
    expect(headers.Authorization).toContain("Basic ");

    const body = JSON.parse(String(options.body));
    expect(body.amount).toBe(500);
    expect(body.currency).toBe("INR");
    expect(body.notes.userId).toBe("user-1");
    expect(body.notes.meditrackOrderId).toBe("mt_order_1");
  });

  test("maps Razorpay API errors to failed-precondition", async () => {
    process.env.RAZORPAY_KEY_ID = "rzp_key";
    process.env.RAZORPAY_KEY_SECRET = "rzp_secret";

    fetchMock.mockResolvedValue({
      ok: false,
      status: 400,
      text: async () =>
        JSON.stringify({
          error: {
            description: "invalid Razorpay payload",
          },
        }),
    });

    await expect(wrapped({ amount: 500 }, authContext())).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });
});
