package ir.ramtung.tinyme.domain.entity;

public class CustomPair {
    private int first;
    private int second;
    public CustomPair(int firstValue, int secondValue){  
        this.first = firstValue;  
        this.second = secondValue; 
    }
    public int getFirst(){
        return this.first;
    }
    public int getSecond(){
        return this.second;
    }
}
