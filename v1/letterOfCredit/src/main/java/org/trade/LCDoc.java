/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;
import com.owlike.genson.Genson;

@DataType()
public class LCDoc {

    private static final Genson genson = new Genson();

    @Property()
    private String docType;

    public LCDoc() {
    }

    public LCDoc(@JsonProperty("docType") String dt) {
        this.docType = dt;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String dt) {
        this.docType = dt;
    }

    public String toJSONString() {
        return genson.serialize(this);
    }

    public static LCDoc fromJSONString(String json) {
        return genson.deserialize(json, LCDoc.class);
    }
}