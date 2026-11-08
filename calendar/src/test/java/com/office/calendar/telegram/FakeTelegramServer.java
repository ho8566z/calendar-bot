package com.office.calendar.telegram;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

// 테스트용 가짜 텔레그램 서버(JDK 내장 HTTP 서버). 받은 요청을 기록하고, 정해 둔 응답을 돌려준다.
class FakeTelegramServer implements AutoCloseable {

    record Request(String method, String pathAndQuery, String body) {
    }

    record Response(int status, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Response> response = new AtomicReference<>(new Response(200, "{\"ok\":true,\"result\":[]}"));

    FakeTelegramServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String uri = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            requests.add(new Request(exchange.getRequestMethod(), uri, body));

            Response r = response.get();
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(r.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(int status, String body) {
        response.set(new Response(status, body));
    }

    List<Request> requests() {
        return requests;
    }

    Request last() {
        return requests.get(requests.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }

}
