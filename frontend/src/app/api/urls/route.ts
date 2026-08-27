import { NextRequest, NextResponse } from "next/server";

function isPrivateOrLocalHost(hostname: string): boolean {
  const lower = hostname.toLowerCase();
  if (
    lower === "localhost" ||
    lower === "127.0.0.1" ||
    lower === "::1" ||
    lower === "0.0.0.0" ||
    lower === "169.254.169.254"
  ) {
    return true;
  }
  // Check IPv4 private ranges: 10.x.x.x, 192.168.x.x, 172.16.x.x - 172.31.x.x
  const ipv4Match = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(lower);
  if (ipv4Match) {
    const o1 = parseInt(ipv4Match[1], 10);
    const o2 = parseInt(ipv4Match[2], 10);
    if (o1 === 10) return true;
    if (o1 === 192 && o2 === 168) return true;
    if (o1 === 172 && o2 >= 16 && o2 <= 31) return true;
    if (o1 === 127) return true;
    if (o1 === 0) return true;
  }
  return false;
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const rawUrl = body?.url;

    if (!rawUrl || typeof rawUrl !== "string" || !rawUrl.trim()) {
      return NextResponse.json(
        { accepted: false, message: "URL must not be blank" },
        { status: 400 }
      );
    }

    let parsed: URL;
    try {
      parsed = new URL(rawUrl.trim());
    } catch {
      return NextResponse.json(
        { accepted: false, message: "Invalid URL format" },
        { status: 400 }
      );
    }

    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      return NextResponse.json(
        { accepted: false, message: "URL must use HTTP or HTTPS protocol" },
        { status: 400 }
      );
    }

    if (isPrivateOrLocalHost(parsed.hostname)) {
      return NextResponse.json(
        { accepted: false, message: "Seed submission of local or internal infrastructure addresses is prohibited" },
        { status: 400 }
      );
    }

    const frontierBase = process.env.URL_FRONTIER_API_URL || "http://localhost:8080";
    const targetEndpoint = `${frontierBase}/urls`;

    const frontierRes = await fetch(targetEndpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({ url: rawUrl.trim() }),
      cache: "no-store",
    });

    const data = await frontierRes.json();
    return NextResponse.json(data, { status: frontierRes.status });
  } catch (error: any) {
    console.error("Failed to proxy URL submission to URL Frontier:", error);
    return NextResponse.json(
      { accepted: false, message: "URL Frontier service unavailable" },
      { status: 503 }
    );
  }
}
