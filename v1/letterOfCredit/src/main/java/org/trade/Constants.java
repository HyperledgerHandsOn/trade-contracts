/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

public class Constants {

    // Ledger key names
    public static final String shippingChannelNameKey = "shippingchannel";
    public static final String tradeContractIdKey = "tradeContractId";
    public static final String shipmentContractIdKey = "shipmentContractId";

    // Remote contract functions names
    public static final String getTradeFunc = "getTrade";
    public static final String getShipmentLocationFunc = "getShipmentLocation";
    public static final String getBillOfLadingFunc = "getBillOfLading";

    // Trade object attributes
    public static final String exporterMSPAttr = "exporterMSP";
    public static final String importerMSPAttr = "importerMSP";
    public static final String tradeStatusAttr = "status";
    public static final String tradeAmountAttr = "amount";
    public static final String tradeDescOfGoodsAttr = "descriptionOfGoods";

    // Bill of Lading attributes
    public static final String blIdAttr = "id";
    public static final String blExpirationDateAttr = "expirationDate";
    public static final String blExporterMSPAttr = "exporterMSP";
    public static final String blCarrierMSPAttr = "carrierMSP";
    public static final String blDescGoodsAttr = "descriptionOfGoods";
    public static final String blAmountAttr = "amount";
    public static final String blBeneficiaryAttr = "beneficiary";
    public static final String blSourcePortAttr = "sourcePort";
    public static final String blDestPortAttr = "destinationPort";

    // MSP Ids
    public static final String exporterOrgMsp = "ExporterOrgMSP";
    public static final String importerOrgMsp = "ImporterOrgMSP";

    // Business roles
    public static final String ANY_ROLE = "any";
    public static final String IMPORTER_BANKER_ROLE = "importer_banker";
    public static final String EXPORTER_BANKER_ROLE = "exporter_banker";
    public static final String IMPORTER_ROLE = "importer";
    public static final String EXPORTER_ROLE = "exporter";

    // Response keywords
    public static final String StatusKey = "Status";
    public static final String LocationKey = "Location";
    public static final String BalanceKey = "Balance";

    // Asset status types
    public static final String REQUESTED = "REQUESTED";
    public static final String ISSUED = "ISSUED";
    public static final String ACCEPTED = "ACCEPTED";

    // Location types
    public static final String sourceLocation = "SOURCE";
    public static final String destinationLocation = "DESTINATION";
}