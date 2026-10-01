package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.service.OrderService;
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

    @PostMapping
    public ResponseEntity<OrderResponse> submitBuyOrder(@Valid @RequestBody SubmitBuyOrderRequest request) {
        OrderResponse response = orderService.submitBuyOrder(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}