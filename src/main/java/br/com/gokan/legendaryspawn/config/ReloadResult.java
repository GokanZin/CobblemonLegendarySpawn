package br.com.gokan.legendaryspawn.config;

import java.util.List;

public record ReloadResult(boolean success, List<String> errors, List<String> warnings) {
    public ReloadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public static ReloadResult ok(List<String> warnings) {
        return new ReloadResult(true, List.of(), warnings);
    }

    public static ReloadResult failed(List<String> errors, List<String> warnings) {
        return new ReloadResult(false, errors, warnings);
    }
}
