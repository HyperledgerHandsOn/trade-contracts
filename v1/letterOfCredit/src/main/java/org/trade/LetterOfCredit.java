/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;
import com.owlike.genson.Genson;
/*
    Letter of Credit JSON format:
    {
        "id": <string>,
        "expirationDate": <string>,
        "beneficiary": <string>,
        "amount": <double>,
        "documents": [ { "docType": <string> }, { "docType": <string> }, ... ],
        "status": <string>
    }
*/

@DataType()
public class LetterOfCredit {

    private static final Genson genson = new Genson();

    @Property()
    private String id;

    @Property()
    private String expirationDate;

    @Property()
    private String beneficiary;

    @Property()
    private double amount;

    @Property()
    private LCDoc[] documents;

    @Property()
    private String status;

    public LetterOfCredit(){
    }

    public LetterOfCredit(@JsonProperty("id") String id, @JsonProperty("expirationDate") String expirationDate, @JsonProperty("beneficiary") String beneficiary, @JsonProperty("amount") double amount, @JsonProperty("requiredDocs") LCDoc[] docs, @JsonProperty("status") String status){
        this.id = id;
        this.expirationDate = expirationDate;
        this.beneficiary = beneficiary;
        this.amount = amount;
        this.documents = docs;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(String beneficiary) {
        this.beneficiary = beneficiary;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LCDoc[] getRequiredDocs() {
        return documents;
    }

    public void setRequiredDocs(LCDoc[] docs) {
        this.documents = docs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String toJSONString() {
        return genson.serialize(this);
    }

    public static LetterOfCredit fromJSONString(String json) {
        return genson.deserialize(json, LetterOfCredit.class);
    }
}
