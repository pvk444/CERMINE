/**
 * This file is part of CERMINE project.
 * Copyright (c) 2011-2018 ICM-UW
 *
 * CERMINE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CERMINE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with CERMINE. If not, see <http://www.gnu.org/licenses/>.
 */

package pl.edu.icm.cermine.bibref.parsing.features;

import java.util.List;
import pl.edu.icm.cermine.bibref.parsing.model.Citation;
import pl.edu.icm.cermine.bibref.parsing.model.CitationToken;
import pl.edu.icm.cermine.tools.classification.general.FeatureCalculator;

/**
 * @author Dominika Tkaczyk (d.tkaczyk@icm.edu.pl)
 */
public class IsVolumeTextFeature extends FeatureCalculator<CitationToken, Citation> {

    @Override
    public double calculateFeatureValue(CitationToken object, Citation context) {
        String text = object.getText();
        if (text.equalsIgnoreCase("vol") || text.equalsIgnoreCase("volume") || text.equalsIgnoreCase("tom")
                || text.equalsIgnoreCase("tome")) {
            return 1;
        }
        List<CitationToken> tokens = context.getTokens();
        int index = tokens.indexOf(object);
        if (index + 2 < tokens.size() && text.equalsIgnoreCase("t")) {
            if (tokens.get(index + 1).getText().matches("^\\d+$")) {
                return 1;
            }
            if (tokens.get(index + 1).getText().equals(".") && tokens.get(index + 2).getText().matches("^\\d+$")) {
                return 1;
            }
        }
        return 0;
    }

}
