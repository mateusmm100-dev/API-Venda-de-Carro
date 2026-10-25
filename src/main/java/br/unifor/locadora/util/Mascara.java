package br.unifor.locadora.util;

/** Mascaramento de dados pessoais para quem não tem permissão de ver o valor completo. */
public final class Mascara {

    private Mascara() {
    }

    /** 12345678909 -> ***.456.789-** */
    public static String cpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return null;
        }
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }

    /** 12345678900 -> *******8900 */
    public static String cnh(String cnh) {
        if (cnh == null || cnh.length() < 4) {
            return null;
        }
        return "*".repeat(cnh.length() - 4) + cnh.substring(cnh.length() - 4);
    }
}
