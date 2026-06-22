package com.sri.miniqueue.exception;

public class CustomException extends Exception{

    CustomException(){ super(); }

    public CustomException(String message){
        super(message);
    }

    public CustomException(Exception e){
        super(e);
    }
}
