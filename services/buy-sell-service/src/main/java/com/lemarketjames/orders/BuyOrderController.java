package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.service.OrderService;
import com.lemarketjames.orders.submission.SubmissionRequestId;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buy-orders")
public class BuyOrderController {

    private final OrderService orderService;

    public BuyOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Every response carries the submission's X-Request-Id, like POST /api/v1/orders (contract C2).
    @PostMapping
    public ResponseEntity<OrderResponse> submitBuyOrder(@Valid @RequestBody SubmitBuyOrderRequest request,
                                                        HttpServletResponse httpResponse) {
        OrderResponse response = orderService.submitBuyOrder(request, SubmissionRequestId.issue(httpResponse));
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}