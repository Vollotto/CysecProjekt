// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.configuration

import com.beust.jcommander.IStringConverter
import org.droidmate.misc.DroidmateException

public class ListOfStringsConverter implements IStringConverter<List<String>>
{
  @Override
  public List<String> convert(String arg)
  {
    assert arg != null

    List<String> convertedArg
    try
    {
      convertedArg = arg.tokenize("[,]").collect {(it as String).trim()}
    } catch (Exception e)
    {
      throw new DroidmateException("The string '${arg}' is not a valid value for parameter expecting a list of strings. " +
        "See command line parameters help for examples of correct format.", e);
    }

    assert convertedArg != null
    return convertedArg
  }
}