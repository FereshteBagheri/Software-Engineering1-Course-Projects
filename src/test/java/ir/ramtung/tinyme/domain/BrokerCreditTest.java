package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class BrokerCreditTest {
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private ContinuousMatcher continuousMatcher;
    @Autowired
    private AuctionMatcher auctionMatcher;


    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().isin("ABC").lastTradePrice(15750).build();
        broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        broker2 = Broker.builder().credit(100000).brokerId(2).build();
        shareholder = Shareholder.builder().shareholderId(1).build();
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
    void not_enough_credit_to_match() {
        Order new_order = new Order(11, security, Side.BUY, 350, 15900, broker2, shareholder);
        MatchResult result = continuousMatcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
    }

    @Test
    void not_enough_credit_for_queue() {
        Order new_order = new Order(11, security, Side.BUY, 7, 15900, broker2, shareholder);
        MatchResult result = continuousMatcher.match(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
    }

    @Test
    void increase_seller_credit_after_matched() {
        long creditBeforeSelling = broker2.getCredit();
        Order new_order = new Order(11, security, Side.SELL, 5, 15700, broker2, shareholder);
        continuousMatcher.match(new_order);
        assertThat(broker2.getCredit()).isEqualTo(creditBeforeSelling + 5*15700);
    }

    @Test
    void decrease_buyer_credit_after_completely_matched() {
        long creditBeforeBuying = broker1.getCredit();
        Order new_order = new Order(11, security, Side.BUY, 5, 15900, broker1, shareholder);
        continuousMatcher.match(new_order);
        assertThat(broker1.getCredit()).isEqualTo(creditBeforeBuying - 5*15800);
    }

    @Test
    void decrease_buyer_credit_after_partially_matched() {
        broker2.increaseCreditBy(350*15800);
        Order new_order = new Order(11, security, Side.BUY, 356, 15805, broker2, shareholder);
        continuousMatcher.execute(new_order);
        assertThat(broker2.getCredit()).isEqualTo(100000+350*15800 - 6*15805);
    }

    @Test
    void rollback_if_not_enough_credit_for_queue() {
        broker2.increaseCreditBy(350*15800);
        Order new_order = new Order(11, security, Side.BUY, 800, 15810, broker2, shareholder);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
        assertThat(broker2.getCredit()).isEqualTo(100000+350*15800);
    }

    @Test
    void unchanged_credit_after_buyer_not_matched() {
        long creditBeforeOrder = broker1.getCredit();
        Order new_order = new Order(11, security, Side.BUY, 360, 15000, broker1, shareholder);
        continuousMatcher.match(new_order);
        assertThat(broker1.getCredit()).isEqualTo(creditBeforeOrder);
    }

    @Test
    void unchanged_credit_after_seller_not_matched() {
        long creditBeforeOrder = broker2.getCredit();
        Order new_order = new Order(11, security, Side.SELL, 360, 15800, broker2, shareholder);
        continuousMatcher.match(new_order);
        assertThat(broker2.getCredit()).isEqualTo(creditBeforeOrder);
    }

    @Test
    void changing_credit_for_same_buyer_seller() {
        long creditBeforeOrder = broker2.getCredit();
        Order new_order = new Order(11, security, Side.SELL, 304, 15700, broker1, shareholder);
        continuousMatcher.match(new_order);
        assertThat(broker2.getCredit()).isEqualTo(creditBeforeOrder);
    }

    @Test
    void unchanged_seller_credit_after_sellOrder_deletion() {
        long creditBeforeOrder = broker2.getCredit();
        DeleteOrderRq deleteReq = new DeleteOrderRq(1,"ABC",Side.SELL,6);
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteReq));
        assertThat(broker2.getCredit()).isEqualTo(creditBeforeOrder);
    }

    @Test
    void rollback_credit_after_buyOrder_deletion() {
        DeleteOrderRq deleteReq = new DeleteOrderRq(1,"ABC",Side.SELL,6);
        long creditBeforeRemove = broker1.getCredit();
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteReq));
        assertThat(broker1.getCredit()).isEqualTo(creditBeforeRemove);
    }

    @Test
    void decrease_buyer_credit_if_order_maintains_priority() {
        long creditBeforeUpdate = broker1.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0,0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker1.getCredit()).isEqualTo( creditBeforeUpdate - 45*15450);
    }

    @Test
    void increase_buyer_credit_if_order_maintains_priority() {
        long creditBeforeUpdate = broker1.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 400,
                15450, 1, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker1.getCredit()).isEqualTo( creditBeforeUpdate + 45*15450);
    }

    @Test
    void unchanged_seller_credit_if_order_maintains_priority() {
        long creditBeforeUpdate = broker2.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 400,
                15800, 2, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker2.getCredit()).isEqualTo( creditBeforeUpdate);
    }

    @Test
    void increase_buyer_credit_if_order_loses_priority_and_does_not_match() {
        long creditBeforeUpdate = broker1.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), Side.BUY, 300,
                15400, 1, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker1.getCredit()).isEqualTo( creditBeforeUpdate + 304*15700 - 300*15400);
    }

    @Test
    void decrease_buyer_credit_if_order_loses_priority_and_does_not_match() {
        long creditBeforeUpdate = broker1.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 445,
                15750, 1, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker1.getCredit()).isEqualTo( creditBeforeUpdate + 445*15450 - 445*15750);
    }

    @Test
    void decrease_buyer_credit_if_order_loses_priority_and_completely_matches() {
        long creditBeforeUpdate = broker1.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 350,
                15800, 1, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker1.getCredit()).isEqualTo( creditBeforeUpdate + 445*15450 - 350*15800);
    }

    @Test
    void decrease_buyer_credit_if_order_loses_priority_and_partially_matches() {
        long creditBeforeUpdate = broker1.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 445,
                15805, 1, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker1.getCredit()).isEqualTo( creditBeforeUpdate + 445*15450 - 350*15800 - 95*15805);
    }

    @Test
    void unchanged_seller_credit_if_order_loses_priority_and_does_not_match() {
        long creditBeforeUpdate = broker2.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 400,
                15750, 2, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker2.getCredit()).isEqualTo( creditBeforeUpdate);
    }
    @Test
    void increase_seller_credit_if_order_loses_priority_and_completely_matches() {
        long creditBeforeUpdate = broker2.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 304,
                15700, 2, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker2.getCredit()).isEqualTo( creditBeforeUpdate + 15700*304);
    }

    @Test
    void increase_seller_credit_if_order_loses_priority_and_partially_matches() {
        long creditBeforeUpdate = broker2.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 350,
                15700, 2, 1, 0, 0, 0);
        assertThatNoException().isThrownBy( () -> security.updateOrder(updateReq, continuousMatcher));
        assertThat(broker2.getCredit()).isEqualTo( creditBeforeUpdate + 15700*304);
    }

    @Test
    void rollback_after_updating_order() {
        long creditBeforeUpdate = broker2.getCredit();
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 5, LocalDateTime.now(), Side.BUY, 1400,
                15810, 2, 1, 0, 0, 0);
        MatchResult result = null;
        try {
            result = security.updateOrder(updateReq, continuousMatcher);
        } catch (Exception e) {
            e.printStackTrace(); // This will print the stack trace of the exception
            System.out.println(e.getMessage()); // This will print the message of the exception
        }
        if (result != null) {
            assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
            assertThat(broker2.getCredit()).isEqualTo(creditBeforeUpdate);
        }
    }

    @Test
    void broker_credit_in_auction_matcher_excute_only(){
        security.setMatchingState(MatchingState.AUCTION);
        broker2.increaseCreditBy(350*15805);
        Order new_order = new Order(11, security, Side.BUY, 356, 15805, broker2, shareholder);
        auctionMatcher.execute(new_order);
        assertThat(broker2.getCredit()).isEqualTo(100000 - 6*15805);
    }

    @Test
    void broker_credit_in_auction_matcher_after_match(){
        security.setMatchingState(MatchingState.AUCTION);
        long creditBeforeMatchBroker1 = broker1.getCredit();
        long creditBeforeMatchBroker2 = broker2.getCredit();
        Order new_order = new Order(11, security, Side.BUY, 450, 15900, broker1, shareholder);
        security.getOrderBook().enqueue(new_order);
        CustomPair pair = security.findOpeningPrice();
        LinkedList<Order> openBuyOrders = security.findOpenOrders(pair.getFirst(), Side.BUY);
        // SellOrder List -> 9, 10, 11
        LinkedList<Order> openSellOrders = security.findOpenOrders(pair.getFirst(), Side.SELL);
        auctionMatcher.match(openBuyOrders, openSellOrders, pair.getFirst());
        // openingPrice = 15810 -> 15900 - 15810 = 90
        assertThat(broker1.getCredit()).isEqualTo(creditBeforeMatchBroker1 + 90*450);
        // 15810 - 15800 = 10
        assertThat(broker2.getCredit()).isEqualTo(creditBeforeMatchBroker2 + 10*350);
        
    }

}
