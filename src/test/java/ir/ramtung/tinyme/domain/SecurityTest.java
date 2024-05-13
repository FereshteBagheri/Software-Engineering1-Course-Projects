package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class SecurityTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private List<Order> orders;
    private List<StopLimitOrder> stopOrders;

    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    @Autowired
    private ContinuousMatcher continuousMatcher;

    @BeforeEach
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().lastTradePrice(15000).build();
        broker = Broker.builder().brokerId(0).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000);
        broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        broker2 = Broker.builder().credit(100000).brokerId(2).build();

        securityRepository.addSecurity(security);
        shareholderRepository.addShareholder(shareholder);
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);

        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        stopOrders = Arrays.asList(
            new StopLimitOrder(11, security, Side.BUY, 300, 15800, broker, shareholder, 16300, 10),
            new StopLimitOrder(12, security, Side.BUY, 43, 15500, broker, shareholder, 16350, 11),
            new StopLimitOrder(13, security, Side.BUY, 445, 15450, broker, shareholder, 16400, 12),
            new StopLimitOrder(14, security, Side.BUY, 526, 15450, broker, shareholder, 16500, 13),
            new StopLimitOrder(15, security, Side.BUY, 1000, 15400, broker, shareholder, 16500, 14),
            new StopLimitOrder(16, security, Side.SELL, 350, 15800, broker, shareholder, 14600, 15),
            new StopLimitOrder(17, security, Side.SELL, 285, 15810, broker, shareholder, 14550, 16),
            new StopLimitOrder(18, security, Side.SELL, 800, 15810, broker, shareholder, 14500, 17),
            new StopLimitOrder(19, security, Side.SELL, 340, 15820, broker, shareholder, 14450, 18),
            new StopLimitOrder(20, security, Side.SELL, 65, 15820, broker, shareholder, 14400, 19)
        );
        stopOrders.forEach(stopOrder -> security.getStopOrderBook().enqueue(stopOrder));
    }

    @Test
    void reducing_quantity_does_not_change_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY, 440, 15450, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getQuantity()).isEqualTo(440);
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void increasing_quantity_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY, 450, 15450, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void changing_price_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1, LocalDateTime.now(), BUY, 300, 15450, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(300);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getPrice()).isEqualTo(15450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(2);
    }

    @Test
    void changing_price_causes_trades_to_happen() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6, LocalDateTime.now(), Side.SELL, 350, 15700, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() ->
                assertThat(security.updateOrder(updateOrderRq, continuousMatcher).trades()).isNotEmpty()
        );
    }

    @Test
    void updating_non_existing_order_fails() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6, LocalDateTime.now(), BUY, 350, 15700, 0, 0, 0, 0, 0);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
    }

    @Test
    void delete_order_works() {
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 6);
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteOrderRq));
        assertThat(security.getOrderBook().getBuyQueue()).isEqualTo(orders.subList(0, 5));
        assertThat(security.getOrderBook().getSellQueue()).isEqualTo(orders.subList(6, 10));
    }

    @Test
    void deleting_non_existing_order_fails() {
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 1);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.deleteOrder(deleteOrderRq));
    }

    @Test
    void increasing_iceberg_peak_size_changes_priority() {
        security = Security.builder().build();
        broker = Broker.builder().credit(1_000_000L).build();
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder),
                new Order(2, security, BUY, 43, 15500, broker, shareholder),
                new IcebergOrder(3, security, BUY, 445, 15450, broker, shareholder, 100),
                new Order(4, security, BUY, 526, 15450, broker, shareholder),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY, 445, 15450, 0, 0, 150, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(150);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void decreasing_iceberg_quantity_to_amount_larger_than_peak_size_does_not_changes_priority() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder),
                new Order(2, security, BUY, 43, 15500, broker, shareholder),
                new IcebergOrder(3, security, BUY, 445, 15450, broker, shareholder, 100),
                new Order(4, security, BUY, 526, 15450, broker, shareholder),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY, 300, 15450, 0, 0, 100, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void update_iceberg_that_loses_priority_with_no_trade_works() {
        security = Security.builder().isin("TEST").build();
        broker = Broker.builder().brokerId(1).credit(100).build();

        security.getOrderBook().enqueue(
                new IcebergOrder(1, security, BUY, 100, 9, broker, shareholder, 10)
        );

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(), BUY, 100, 10, 0, 0, 10, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateReq, continuousMatcher));

        assertThat(broker.getCredit()).isEqualTo(0);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(1);
    }

    @Test
    void update_iceberg_order_decrease_peak_size() {
        security = Security.builder().isin("TEST").build();
        security.getOrderBook().enqueue(
                new IcebergOrder(1, security, BUY, 20, 10, broker, shareholder, 10)
        );

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(), BUY, 20, 10, 0, 0, 5, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateReq, continuousMatcher));

        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void update_iceberg_order_price_leads_to_match_as_new_order() throws InvalidRequestException {
        security = Security.builder().isin("TEST").build();
        shareholder.incPosition(security, 1_000);
        orders = List.of(
                new Order(1, security, BUY, 15, 10, broker, shareholder),
                new Order(2, security, BUY, 20, 10, broker, shareholder),
                new Order(3, security, BUY, 40, 10, broker, shareholder),
                new IcebergOrder(4, security, SELL, 30, 12, broker, shareholder, 10)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(5, security.getIsin(), 4, LocalDateTime.now(), SELL, 30, 10, 0, 0, 10, 0, 0);

        MatchResult result = security.updateOrder(updateReq, continuousMatcher);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.trades()).hasSize(2);
        assertThat(result.remainder().getQuantity()).isZero();
    }

    @Test
    void enqueue_order(){
        Broker broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        Order order = new Order(21, security, Side.BUY, 304, 15700, broker1, shareholder);
        security.enqueueOrder(order);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(22, security, Side.BUY, 300, 15800, broker1, shareholder, 16300, 20);
        security.enqueueOrder(stopLimitOrder);

        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 22)).isNotEqualTo(null);
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 22)).isEqualTo(null);
    }

    @Test
    void remove_order_by_orderID(){
        Broker broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        Order order = new Order(21, security, Side.BUY, 304, 15700, broker1, shareholder);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(22, security, Side.BUY, 300, 15800, broker1, shareholder, 16300, 20);
        security.getStopOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(order);

        security.removeOrderByOrderId(order, Side.BUY, 21);
        security.removeOrderByOrderId(stopLimitOrder, Side.BUY, 22);

        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 22)).isEqualTo(null);
    }

    @Test
    void find_triggered_orders_buy_side(){
        int lastTradePrice = 16450;
        LinkedList<StopLimitOrder> valid_activeOrders = new LinkedList<>();
        for (int i = 11; i <= 13; i++){
            valid_activeOrders.add((StopLimitOrder)security.getStopOrderBook().findByOrderId(Side.BUY, i));
        }
        LinkedList<StopLimitOrder> activeOrders = security.findTriggeredOrders(lastTradePrice);
        assertThat(activeOrders).isEqualTo(valid_activeOrders);
    }

    @Test
    void find_triggered_orders_sell_side(){
        int lastTradePrice = 14530;
        LinkedList<StopLimitOrder> valid_activeOrders = new LinkedList<>();
        for (int i = 16; i <= 17; i++){
            valid_activeOrders.add((StopLimitOrder)security.getStopOrderBook().findByOrderId(Side.SELL, i));
        }
        LinkedList<StopLimitOrder> activeOrders = security.findTriggeredOrders(lastTradePrice);
        assertThat(activeOrders).isEqualTo(valid_activeOrders);
    }

    @Test
    void find_opening_price_only_one_with_max_exchanged_quantity(){
        List<Order> newOrders = Arrays.asList(
            new Order(11, security, Side.BUY, 304, 15800, broker, shareholder),
            new Order(12, security, Side.BUY, 43, 15900, broker, shareholder),
            new Order(13, security, Side.BUY, 445, 16000, broker, shareholder),
            new Order(14, security, Side.SELL, 350, 15600, broker, shareholder),
            new Order(15, security, Side.SELL, 285, 15430, broker, shareholder)
        );
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));

        CustomPair pair = security.findOpeningPrice();
        int validOpeningPrice = 15800;
        int validExchangedQuantity = 792;
        assertThat(pair.getFirst()).isEqualTo(validOpeningPrice);
        assertThat(pair.getSecond()).isEqualTo(validExchangedQuantity);
    }

    @Test
    void find_opening_price_with_two_different_prices_and_differences_to_last_trade_price(){
        security.setLastTradePrice(15804);
        List<Order> newOrders = Arrays.asList(
                new Order(11, security, Side.BUY, 304, 15805, broker, shareholder),
                new Order(12, security, Side.BUY, 43, 15900, broker, shareholder),
                new Order(13, security, Side.BUY, 445, 16000, broker, shareholder),
                new Order(14, security, Side.SELL, 350, 15600, broker, shareholder),
                new Order(15, security, Side.SELL, 285, 15430, broker, shareholder)
        );
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));

        CustomPair pair = security.findOpeningPrice();
        int validOpeningPrice = 15805;
        int validExchangedQuantity = 792;
        assertThat(pair.getFirst()).isEqualTo(validOpeningPrice);
        assertThat(pair.getSecond()).isEqualTo(validExchangedQuantity);
    }

    @Test
    void find_opening_price_two_valid_price_with_same_difference_to_last_trade_price(){
        security.setLastTradePrice(15803);
        List<Order> newOrders = Arrays.asList(
                new Order(11, security, Side.BUY, 304, 15806, broker, shareholder),
                new Order(12, security, Side.BUY, 43, 15900, broker, shareholder),
                new Order(13, security, Side.BUY, 445, 16000, broker, shareholder),
                new Order(14, security, Side.SELL, 350, 15600, broker, shareholder),
                new Order(15, security, Side.SELL, 285, 15430, broker, shareholder)
        );
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));

        CustomPair pair = security.findOpeningPrice();
        int validOpeningPrice = 15800;
        int validExchangedQuantity = 792;
        assertThat(pair.getFirst()).isEqualTo(validOpeningPrice);
        assertThat(pair.getSecond()).isEqualTo(validExchangedQuantity);
    }

    @Test
    void find_open_orders(){
        List<Order> newOrders = Arrays.asList(
            new Order(11, security, Side.BUY, 304, 15800, broker, shareholder),
            new Order(12, security, Side.BUY, 43, 15900, broker, shareholder),
            new Order(13, security, Side.BUY, 445, 16000, broker, shareholder),
            new Order(14, security, Side.SELL, 350, 15600, broker, shareholder),
            new Order(15, security, Side.SELL, 285, 15430, broker, shareholder)
        );
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));

        CustomPair pair = security.findOpeningPrice();

        LinkedList<Order> openBuyOrders = security.findOpenOrders(pair.getFirst(), Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(pair.getFirst(), Side.SELL);

        LinkedList<Order> validBuyQueue = new LinkedList<>();
        LinkedList<Order> validSellQueue = new LinkedList<>();

        for (int i = 13; i >= 11; i--){
            validBuyQueue.add((Order)security.getOrderBook().findByOrderId(Side.BUY, i));
        }
        for (int i = 15; i >= 6; i--){
            if (i >= 14 || i==6)
                validSellQueue.add((Order)security.getOrderBook().findByOrderId(Side.SELL, i));
        }
        assertThat(openBuyOrders).isEqualTo(validBuyQueue);
        assertThat(openSellOrders).isEqualTo(validSellQueue);
    }

    

}