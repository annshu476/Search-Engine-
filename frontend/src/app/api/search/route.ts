import { NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const q = searchParams.get("q") || "";
  const page = searchParams.get("page") || "0";
  const size = searchParams.get("size") || "10";
  const sort = searchParams.get("sort") || "relevance";
  const language = searchParams.get("language");
  const contentType = searchParams.get("contentType");
  const statusCode = searchParams.get("statusCode");
  const fromDate = searchParams.get("fromDate");
  const toDate = searchParams.get("toDate");

  // Construct target query parameters
  const targetParams = new URLSearchParams();
  if (q) targetParams.set("q", q);
  targetParams.set("page", page);
  targetParams.set("size", size);
  targetParams.set("sort", sort);
  if (language) targetParams.set("language", language);
  if (contentType) targetParams.set("contentType", contentType);
  if (statusCode) targetParams.set("statusCode", statusCode);
  if (fromDate) targetParams.set("fromDate", fromDate);
  if (toDate) targetParams.set("toDate", toDate);

  const backendBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8083";
  const targetUrl = `${backendBaseUrl}/api/search?${targetParams.toString()}`;

  // Forward or generate a correlation ID
  const incomingRequestId = request.headers.get("X-Request-Id");
  const requestId = incomingRequestId && /^[a-zA-Z0-9._-]{1,64}$/.test(incomingRequestId)
    ? incomingRequestId
    : crypto.randomUUID();

  try {
    const response = await fetch(targetUrl, {
      method: "GET",
      headers: {
        "X-Request-Id": requestId,
        "Accept": "application/json",
      },
      next: { revalidate: 0 }, // Disable server cache to forward directly
    });

    const data = await response.json();

    if (!response.ok) {
      return NextResponse.json(data, {
        status: response.status,
        headers: {
          "X-Request-Id": requestId,
          ...(response.headers.get("Retry-After") ? { "Retry-After": response.headers.get("Retry-After")! } : {}),
        },
      });
    }

    return NextResponse.json(data, {
      status: 200,
      headers: {
        "X-Request-Id": requestId,
      },
    });
  } catch (error: any) {
    console.error("Proxy search request failed:", error);
    return NextResponse.json(
      { error: "Unable to connect to the search service" },
      { status: 503 }
    );
  }
}
