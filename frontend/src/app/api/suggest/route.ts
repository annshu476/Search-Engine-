import { NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const q = searchParams.get("q") || "";

  if (!q || q.trim().length < 2) {
    return NextResponse.json(
      { error: "Search prefix must contain at least 2 characters" },
      { status: 400 }
    );
  }

  const backendBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8083";
  const targetUrl = `${backendBaseUrl}/api/search/suggest?q=${encodeURIComponent(q.trim())}`;

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
      next: { revalidate: 0 },
    });

    const data = await response.json();

    if (!response.ok) {
      return NextResponse.json(data, {
        status: response.status,
        headers: {
          "X-Request-Id": requestId,
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
    console.error("Proxy suggest request failed:", error);
    return NextResponse.json(
      { error: "Search suggestion service temporarily unavailable" },
      { status: 503 }
    );
  }
}
