package com.example.moneymanagerpro;

/** Candidate detection only: a possible match always requires the user's explicit link. */
final class LinkedLoanMatchPolicy {
    private LinkedLoanMatchPolicy() { }

    static boolean possibleSameLoan(String localName, double localPrincipal, double localEmi,
                                    String remoteName, long remotePrincipal, long remoteEmi) {
        boolean sameName = localName != null && remoteName != null && !localName.trim().isEmpty()
                && localName.trim().equalsIgnoreCase(remoteName.trim());
        boolean sameTerms = remotePrincipal > 0 && remoteEmi > 0
                && localPrincipal == remotePrincipal && localEmi == remoteEmi;
        return sameName || sameTerms;
    }
}
