/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;
import com.owlike.genson.Genson;
/*
    Export License JSON format:
    {
        "id": <string>,
        "expirationDate": <string>,
        "exporter": <string>,
        "carrier": <string>,
        "descriptionOfGoods": <string>,
        "approver": <string>,
        "status": <string>
    }
*/

@DataType()
public class ExportLicense {

    @Property()
    private String id;

    @Property()
    private String expirationDate;

    @Property()
    private String exporter;

    @Property()
    private String carrier;

    @Property()
    private String descriptionOfGoods;

    @Property()
    private String approver;

    @Property()
    private String status;

    private static final Genson genson = new Genson();

    public ExportLicense(@JsonProperty("id") String id, @JsonProperty("expirationDate") String expirationDate, @JsonProperty("exporter") String exporter, @JsonProperty("carrier") String carrier, @JsonProperty("descriptionOfGoods") String descriptionOfGoods, @JsonProperty("approver") String approver, @JsonProperty("status") String status) {
        this.id = id;
        this.expirationDate = expirationDate;
        this.exporter = exporter;
        this.carrier = carrier;
        this.descriptionOfGoods = descriptionOfGoods;
        this.approver = approver;
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

    public String getExporter() {
        return exporter;
    }

    public void setExporter(String exporter) {
        this.exporter = exporter;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getDescriptionOfGoods() {
        return descriptionOfGoods;
    }

    public void setDescriptionOfGoods(String descriptionOfGoods) {
        this.descriptionOfGoods = descriptionOfGoods;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
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

    public static ExportLicense fromJSONString(String json) {
        return genson.deserialize(json, ExportLicense.class);
    }
}
