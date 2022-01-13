/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.diagnostics.internal.variantreports.formatter;

import javax.annotation.Nullable;
import java.util.Optional;

public class VariantReportSpec {
    private final ReportType reportType;
    @Nullable private final String searchTarget;

    public VariantReportSpec(ReportType reportType, @Nullable String searchTarget) {
        this.reportType = reportType;
        this.searchTarget = searchTarget;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public Optional<String> getSearchTarget() {
        return Optional.ofNullable(searchTarget);
    }

    public enum ReportType {
        OUTGOING("variant", "outgoing", true, true, true),
        RESOLVABLE("configuration", "resolvable", false, false, false);

        private final String reportedTypeAlias;
        private final String direction;
        private final boolean includeCapabilities;
        private final boolean includeArtifacts;
        private final boolean includeVariants;

        ReportType(String reportedTypeAlias, String direction, boolean includeCapabilities, boolean includeArtifacts, boolean includeVariants) {
            this.reportedTypeAlias = reportedTypeAlias;
            this.direction = direction;
            this.includeCapabilities = includeCapabilities;
            this.includeVariants = includeVariants;
            this.includeArtifacts = includeArtifacts;
        }

        public String getReportedTypeAlias() {
            return reportedTypeAlias;
        }

        public String getDirection() {
            return direction;
        }

        public String getFullReportedTypeDesc() {
            return direction + " " + reportedTypeAlias + "s";
        }

        public boolean isIncludeCapabilities() {
            return includeCapabilities;
        }

        public boolean isIncludeArtifacts() {
            return includeArtifacts;
        }

        public boolean isIncludeVariants() {
            return includeVariants;
        }
    }
}
