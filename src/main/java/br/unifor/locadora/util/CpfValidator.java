package br.unifor.locadora.util;

public final class CpfValidator {

    private CpfValidator() {
    }

    /** Valida um CPF já normalizado (11 dígitos) pelo cálculo dos dígitos verificadores. */
    public static boolean valido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}")) {
            return false;
        }
        if (cpf.chars().distinct().count() == 1) {
            return false; // 000.000.000-00, 111.111.111-11 etc.
        }
        int d1 = digito(cpf, 9, 10);
        int d2 = digito(cpf, 10, 11);
        return d1 == Character.getNumericValue(cpf.charAt(9))
                && d2 == Character.getNumericValue(cpf.charAt(10));
    }

    private static int digito(String cpf, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += Character.getNumericValue(cpf.charAt(i)) * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
