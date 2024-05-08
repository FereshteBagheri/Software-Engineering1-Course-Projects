package ir.ramtung.tinyme.messaging.event;

public class OpeningPriceEvent extends Event{
    private String securityIsin;
    private int openingPrice;
    private int tradableQuantity;
}
