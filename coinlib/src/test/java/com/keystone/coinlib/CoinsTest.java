/*
 * Copyright (c) 2021 Keystone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.keystone.coinlib;

import com.keystone.coinlib.exception.InvalidPathException;
import com.keystone.coinlib.path.AddressIndex;
import com.keystone.coinlib.path.CoinPath;
import com.keystone.coinlib.utils.Coins;

import org.junit.Test;

import static com.keystone.coinlib.path.CoinPath.m;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoinsTest {
    @Test
    public void testCoinCode() {
        assertEquals(Coins.coinCodeFromCoinId("bitcoin"), "BTC");
        assertEquals(Coins.coinIdFromCoinCode("BTC"), "bitcoin");
    }

    @Test
    public void testCoinPath() throws InvalidPathException {
        String path = "m/49'/0'/0'/0/0";
        AddressIndex add = CoinPath.parsePath(path);
        assertEquals(add.toString(), path);
        assertEquals(m().purpose49().coinType(0).account(0).external().address(0).toString(), path);

        String path1 = "M/49'/0'/0'/0/1";

        assertEquals(1, CoinPath.parsePath(path1).getValue());
    }

    @Test
    public void testCurve() {
        assertEquals(Coins.CURVE.SECP256K1, Coins.curveFromCoinCode("BTC"));
    }

    @Test
    public void testSupportedCoin() {
        assertTrue(Coins.isCoinSupported("BTC"));
    }
}
