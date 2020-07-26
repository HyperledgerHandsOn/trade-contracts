/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

import { Object, Property } from 'fabric-contract-api';

@Object()
export class TradeAgreement {

    @Property()
    public tradeID: string;

    @Property()
    public exporterMSP: string;

    @Property()
    public importerMSP: string;

    @Property()
    public amount: number;

    @Property()
    public descriptionOfGoods: string;

    @Property()
    public status: string;
}
