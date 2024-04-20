package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StopOrderBookTest {
    private Security security;
    private List<StopLimitOrder> orders;
    private Shareholder shareholder;
    @BeforeEach
    void setupStopOrderBook() {
        security = Security.builder().build();
        Broker broker = Broker.builder().build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orders = Arrays.asList(
                new StopLimitOrder(1, security, Side.BUY, 304, 15700, broker, shareholder, 10),
                new StopLimitOrder(2, security, Side.BUY, 43, 15500, broker, shareholder, 20),
                new StopLimitOrder(3, security, Side.BUY, 445, 15450, broker, shareholder, 30),
                new StopLimitOrder(4, security, Side.BUY, 526, 15450, broker, shareholder, 40),
                new StopLimitOrder(5, security, Side.BUY, 1000, 15400, broker, shareholder, 50),
                new StopLimitOrder(6, security, Side.SELL, 350, 15800, broker, shareholder, 10),
                new StopLimitOrder(7, security, Side.SELL, 285, 15810, broker, shareholder, 20),
                new StopLimitOrder(8, security, Side.SELL, 800, 15810, broker, shareholder, 30),
                new StopLimitOrder(9, security, Side.SELL, 340, 15820, broker, shareholder, 40),
                new StopLimitOrder(10, security, Side.SELL, 65, 15820, broker, shareholder, 50)
        );
        orders.forEach(stopOrder -> security.getStopOrderBook().enqueue(stopOrder));
    }

    @Test
    void finds_the_first_stop_order_by_id() {
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 1))
                .isEqualTo(orders.get(0));
    }

    @Test
    void fails_to_find_the_first_stop_order_by_id_in_the_wrong_queue() {
        assertThat(security.getStopOrderBook().findByOrderId(Side.SELL, 1)).isNull();
    }

    @Test
    void finds_some_stop_order_in_the_middle_by_id() {
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 3))
                .isEqualTo(orders.get(2));
    }

    @Test
    void finds_the_last_stop_order_by_id() {
        assertThat(security.getStopOrderBook().findByOrderId(Side.SELL, 10))
                .isEqualTo(orders.get(9));
    }

    @Test
    void removes_the_first_stop_order_by_id() {
        StopOrderBook stopOrderBook = security.getStopOrderBook();
        stopOrderBook.removeByOrderId(Side.BUY, 1);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(orders.subList(1, 5));
    }

    @Test
    void fails_to_remove_the_first_stop_order_by_id_in_the_wrong_queue() {
        StopOrderBook stopOrderBook = security.getStopOrderBook();
        stopOrderBook.removeByOrderId(Side.SELL, 1);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(orders.subList(0, 5));
    }

    @Test
    void removes_the_last_stop_order_by_id() {
        StopOrderBook stopOrderBook = security.getStopOrderBook();
        stopOrderBook.removeByOrderId(Side.SELL, 10);
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(orders.subList(5, 9));
    }

    @Test
    void check_buy_queue_arrangement_is_increasing() {
        List<StopLimitOrder> buyQueue = security.getStopOrderBook().getBuyQueue();
        assertThat(buyQueue).hasSize(5);
        assertThat(buyQueue.get(0).getOrderId()).isEqualTo(1);
        assertThat(buyQueue.get(1).getOrderId()).isEqualTo(2);
        assertThat(buyQueue.get(2).getOrderId()).isEqualTo(3);
        assertThat(buyQueue.get(3).getOrderId()).isEqualTo(4);
        assertThat(buyQueue.get(4).getOrderId()).isEqualTo(5);
    }

    @Test
    void check_sell_queue_arrangement_is_decreasing() {
        List<StopLimitOrder> sellQueue = security.getStopOrderBook().getSellQueue();
        assertThat(sellQueue).hasSize(5);
        assertThat(sellQueue.get(0).getOrderId()).isEqualTo(10);
        assertThat(sellQueue.get(1).getOrderId()).isEqualTo(9);
        assertThat(sellQueue.get(2).getOrderId()).isEqualTo(8);
        assertThat(sellQueue.get(3).getOrderId()).isEqualTo(7);
        assertThat(sellQueue.get(4).getOrderId()).isEqualTo(6);
    }
    
    @Test
    void buy_stop_order_is_activatable() {
        List<StopLimitOrder> buyQueue = security.getStopOrderBook().getBuyQueue();
        int lastTradePrice = 30;
        List<StopLimitOrder> activatableBuyOrders = security.getStopOrderBook().getActivatableOrders(lastTradePrice, Side.BUY);
        assertThat(buyQueue).hasSize(2);
        assertThat(activatableBuyOrders).hasSize(3);
        assertThat(activatableBuyOrders.get(0).getOrderId()).isEqualTo(1);
        assertThat(activatableBuyOrders.get(1).getOrderId()).isEqualTo(2);
        assertThat(activatableBuyOrders.get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void sell_stop_order_is_activatable() {
        List<StopLimitOrder> sellQueue = security.getStopOrderBook().getSellQueue();
        int lastTradePrice = 40;
        List<StopLimitOrder> activatableSellOrders = security.getStopOrderBook().getActivatableOrders(lastTradePrice, Side.SELL);
        assertThat(sellQueue).hasSize(3);
        assertThat(activatableSellOrders).hasSize(2);
        assertThat(activatableSellOrders.get(0).getOrderId()).isEqualTo(10);
        assertThat(activatableSellOrders.get(1).getOrderId()).isEqualTo(9);
    }
    
    @Test
    void total_sell_quantity_by_shareholder_works() {
        int totalSellQuantity = security.getStopOrderBook().totalSellQuantityByShareholder(shareholder);
        assertThat(totalSellQuantity).isEqualTo(1840);
    }
}