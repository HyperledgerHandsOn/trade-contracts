/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;
import com.owlike.genson.Genson;

@DataType()
public class BankAccount {

    private static final Genson genson = new Genson();

    @Property()
    private String ownerMSP;

    @Property()
    private String bank;

    @Property()
    private double balance;

    public BankAccount() {
    }

    public BankAccount(@JsonProperty("ownerMSP") String ownerMSP, @JsonProperty("bank") String bank, @JsonProperty("balance") double balance) {
        this.ownerMSP = ownerMSP;
        this.bank = bank;
        this.balance = balance;
    }

    public String getOwnerMSP() {
        return ownerMSP;
    }

    public void setOwnerMSP(String ownerMSP) {
        this.ownerMSP = ownerMSP;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String toJSONString() {
        return genson.serialize(this);
    }

    public static BankAccount fromJSONString(String json) {
        return genson.deserialize(json, BankAccount.class);
    }
}