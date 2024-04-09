package ir.ramtung.tinyme.domain;


import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.discovery.SelectorResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class MinimumExecutionQuantityTest {
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;
    @Autowired
    OrderHandler orderhandler;



    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().isin("ABC").build();
        broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        broker2 = Broker.builder().credit(100000).brokerId(2).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker1, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker1, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker1, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker2, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker2, shareholder),
                new Order(7, security, Side.SELL, 985, 15810, broker1, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker2, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker2, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker2, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void create_enter_order_request_with_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 10);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(10);
    }

    @Test
    void create_enter_order_request_without_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(0);
    }

    @Test
    void create_update_order_request_with_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 10);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(10);
    }

    @Test
    void create_update_order_request_without_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(0);
    }

    @Test
    void validate_minimum_execution_quantity_works() {
        EnterOrderRq valid = EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 10);
    }

    @Test
    void buy_order_with_minimum_execution_quantity_completely_matched() {
        Order new_order = new Order(11, security, Side.BUY,
                350, 15800, broker1, shareholder,
                LocalDateTime.now(), 40, false);
        MatchResult result = matcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
    }

    @Test
    void sell_order_with_minimum_execution_quantity_completely_matched() {
        Order new_order = new Order(11, security, Side.SELL,
                300, 15600, broker2, shareholder,
                LocalDateTime.now(), 40, false);
        MatchResult result = matcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
    }


    @Test
    void buy_order_with_minimum_execution_quantity_minimum_amount_matched() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15800, broker1, shareholder,
                LocalDateTime.now(), 350, false);
        MatchResult result = matcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(50);
    }

    @Test
    void sell_order_with_minimum_execution_quantity_minimum_amount_matched() {
        Order new_order = new Order(11, security, Side.SELL,
                400, 15700, broker1, shareholder,
                LocalDateTime.now(), 304, false);
        MatchResult result = matcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(96);
    }

    @Test
    void sell_order_with_minimum_execution_quantity_partially_matched() {
        Order new_order = new Order(11, security, Side.SELL,
                400, 15700, broker1, shareholder,
                LocalDateTime.now(), 250, false);
        MatchResult result = matcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(96);
    }
    @Test
    void buy_order_with_minimum_execution_quantity_partially_matched() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15800, broker1, shareholder,
                LocalDateTime.now(), 40, false);
        MatchResult result = matcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(50);
    }
    @Test
    void validate_minimum_execution_quantity_fails() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 500);
        EnterOrderRq newReq2 = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, -10);
    }


    @Test
    void update_order_request_with_same_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 10);
        orderhandler.handleEnterOrder(newReq);
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 5,
                15900, 1, 1, 0, 10);
//        assertThrows(InvalidRequestException.class , );
    }

}






