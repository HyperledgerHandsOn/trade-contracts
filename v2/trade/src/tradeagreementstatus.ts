/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

import { Object, Property } from 'fabric-contract-api';

@Object()
export class TradeAgreementStatus {

    @Property()
    public Status: string;
}
