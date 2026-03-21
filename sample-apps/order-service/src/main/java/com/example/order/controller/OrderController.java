package com.example.order.controller;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 REST API 컨트롤러.
 *
 * <p>모든 엔드포인트는 /api/orders 경로 하위에 위치한다.
 * 입력값 검증(@Valid)은 컨트롤러에서 수행하고, 비즈니스 로직은 OrderService에 위임한다.</p>
 *
 * <p>Swagger UI 접근: /swagger-ui.html</p>
 */
@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문을 생성한다.
     *
     * <p>POST /api/orders<br>
     * 요청 본문의 유효성 검증 실패 시 400 Bad Request를 반환한다.
     * 정상 처리 시 201 Created와 함께 생성된 주문 정보를 반환한다.</p>
     *
     * @param request 주문 생성 요청 DTO (@Valid로 Bean Validation 수행)
     * @return 생성된 주문 정보
     */
    @Operation(summary = "주문 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    /**
     * 단건 주문을 상세 조회한다.
     *
     * <p>GET /api/orders/{id}<br>
     * 존재하지 않는 ID 요청 시 404 Not Found를 반환한다.</p>
     *
     * @param id 조회할 주문 ID (경로 변수)
     * @return 주문 상세 정보
     */
    @Operation(summary = "주문 상세 조회")
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    /**
     * 전체 주문 목록을 조회한다.
     *
     * <p>GET /api/orders<br>
     * 주문이 없으면 빈 배열을 반환한다.</p>
     *
     * @return 주문 목록
     */
    @Operation(summary = "주문 목록 조회")
    @GetMapping
    public List<OrderResponse> getOrders() {
        return orderService.getOrders();
    }
}
