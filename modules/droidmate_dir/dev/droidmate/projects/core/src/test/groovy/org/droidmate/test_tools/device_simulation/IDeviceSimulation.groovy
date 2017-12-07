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
package org.droidmate.test_tools.device_simulation

import com.google.common.annotations.VisibleForTesting
import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.device.datatypes.IAndroidDeviceAction
import org.droidmate.device.datatypes.IDeviceGuiSnapshot

interface IDeviceSimulation
{
  void updateState(IAndroidDeviceAction deviceAction)

  IDeviceGuiSnapshot getCurrentGuiSnapshot()

  List<ITimeFormattedLogcatMessage> getCurrentLogs()

  String getPackageName()

  @VisibleForTesting
  List<IGuiScreen> getGuiScreens()

  void assertEqual(IDeviceSimulation other)

  boolean getAppIsRunning()
}