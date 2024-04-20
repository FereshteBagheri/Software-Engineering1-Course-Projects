package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StopOrderTest {
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

}
