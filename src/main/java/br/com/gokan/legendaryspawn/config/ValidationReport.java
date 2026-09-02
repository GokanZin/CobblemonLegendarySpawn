package br.com.gokan.legendaryspawn.config;

import java.util.List;

public record ValidationReport(List<String> errors, List<String> warnings) {
    public ValidationReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
