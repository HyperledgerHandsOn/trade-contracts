/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

public class Constants {

    // Ledger key names
    public static final String tradeChannelNameKey = "tradechannel";
    public static final String tradeContractIdKey = "tradeContractId";
    public static final String carrierMSPAttr = "carrierMSP";
    public static final String regulatoryAuthorityMSPAttr = "regulatoryAuthorityMSP";

    // Remote contract functions names
    public static final String getTradeFunc = "getTrade";
    public static final String getLCStatusFunc = "getLCStatus";

    // Trade object attributes
    public static final String exporterMSPAttr = "exporterMSP";
    public static final String importerMSPAttr = "importerMSP";
    public static final String tradeStatusAttr = "status";
    public static final String tradeAmountAttr = "amount";
    public static final String tradeDescOfGoodsAttr = "descriptionOfGoods";

    // L/C object attributes
    public static final String lcStatusAttr = "status";
    public static final String lcIdAttr = "id";
    public static final String lcExpirationDateAttr = "expirationDate";
    public static final String lcBeneficiaryAttr = "beneficiary";
    public static final String lcAmountsAttr = "amount";
    public static final String lcDocumentsAttr = "documents";

    // MSP Ids
    public static final String exporterOrgMsp = "ExporterOrgMSP";
    public static final String regulatorOrgMsp = "RegulatorOrgMSP";

    // Business roles
    public static final String ANY_ROLE = "any";
    public static final String EXPORTER_ROLE = "exporter";
    public static final String REGULATOR_ROLE = "regulator";

    // Response keywords
    public static final String StatusKey = "Status";

    // Asset status types
    public static final String REQUESTED = "REQUESTED";
    public static final String ISSUED = "ISSUED";
    public static final String ACCEPTED = "ACCEPTED";
}