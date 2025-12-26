/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.borrowing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BorrowingModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(BorrowingPackage.eINSTANCE);
        assertNotNull(BorrowingFactory.eINSTANCE);
    }

    @Test
    void testCreateBorrower() {
        Borrower borrower = BorrowingFactory.eINSTANCE.createBorrower();
        borrower.setName("Alice");
        borrower.setMemberId("M001");
        assertEquals("Alice", borrower.getName());
        assertEquals("M001", borrower.getMemberId());
    }

    @Test
    void testCreateLoan() {
        Loan loan = BorrowingFactory.eINSTANCE.createLoan();
        assertNotNull(loan);
    }
}
