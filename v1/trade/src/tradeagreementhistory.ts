/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

import { Object, Property } from 'fabric-contract-api';
import { TradeAgreement } from './tradeagreement';

@Object()
export class TradeAgreementHistory {

    @Property()
    public txId: string;

    @Property()
    public timestamp: string;

    @Property()
    public isDelete: string;

    @Property()
    public tradeAgreement: TradeAgreement;
}
