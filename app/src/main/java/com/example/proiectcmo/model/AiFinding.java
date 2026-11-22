package com.example.proiectcmo.model;

import java.util.ArrayList;
import java.util.List;

public class AiFinding {
    public enum Status { ACTIVE, CANCELED, LAPSED, UNCERTAIN }

    public String name;
    public String vendor;
    public String currency;
    public String billingCycle;
    public String matchesExistingId;
    public String reason;
    public double price;
    public long lastChargeDate;
    public long nextPaymentDate;
    public Status status = Status.UNCERTAIN;
    public List<String> evidenceIds = new ArrayList<>();
}
