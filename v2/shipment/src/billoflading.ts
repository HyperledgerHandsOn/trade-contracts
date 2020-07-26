/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

import { Object, Property } from 'fabric-contract-api';

@Object()
export class BillOfLading {

    @Property()
    public id: string;

    @Property()
    public expirationDate: string;

    @Property()
    public exporterMSP: string;

    @Property()
    public carrierMSP: string;

    @Property()
    public descriptionOfGoods: string;

    @Property()
    public amount: number;

    @Property()
    public beneficiary: string;

    @Property()
    public sourcePort: string;

    @Property()
    public destinationPort: string;
}
