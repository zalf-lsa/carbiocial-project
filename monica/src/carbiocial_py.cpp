/* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
Authors:
Michael Berg <michael.berg@zalf.de>

Maintainers:
Currently maintained by the authors.

This file is part of the MONICA model in the Carbiocial project.
Copyright (C) Leibniz Centre for Agricultural Landscape Research (ZALF)
*/

#include "carbiocial.h"
#include <boost/python.hpp>

using namespace Carbiocial;
using namespace boost::python;
using namespace std;

dict rcs(CarbiocialConfiguration* cc)
{
	auto m = runCarbiocialSimulation(cc);
	dict d;
	for(auto y2y : m)
	{
		d[y2y.first] = y2y.second;

	}
	return d;
}


BOOST_PYTHON_MODULE(carbiocial_py)
{
	class_<CarbiocialConfiguration>("CarbiocialConfiguration")
			.add_property("climateFile", &CarbiocialConfiguration::getClimateFile, &CarbiocialConfiguration::setClimateFile)
			.add_property("pathToIniFile", &CarbiocialConfiguration::getPathToIniFile, &CarbiocialConfiguration::setPathToIniFile)
			.add_property("inputPath", &CarbiocialConfiguration::getInputPath, &CarbiocialConfiguration::setInputPath)
			.add_property("outputPath", &CarbiocialConfiguration::getOutputPath, &CarbiocialConfiguration::setOutputPath)
			.add_property("startDate", &CarbiocialConfiguration::getStartDate, &CarbiocialConfiguration::setStartDate)
			.add_property("endDate", &CarbiocialConfiguration::getEndDate, &CarbiocialConfiguration::setEndDate)
			.add_property("rowId", &CarbiocialConfiguration::getRowId, &CarbiocialConfiguration::setRowId)
			.add_property("colId", &CarbiocialConfiguration::getColId, &CarbiocialConfiguration::setColId)
			.add_property("latitude", &CarbiocialConfiguration::getLatitude, &CarbiocialConfiguration::setLatitude)
			.add_property("elevation", &CarbiocialConfiguration::getElevation, &CarbiocialConfiguration::setElevation)
			.add_property("profileId", &CarbiocialConfiguration::getProfileId, &CarbiocialConfiguration::setProfileId)

			.def_readwrite("writeOutputFiles", &CarbiocialConfiguration::writeOutputFiles)
			.def_readwrite("create2014To2040ClimateData", &CarbiocialConfiguration::create2013To2040ClimateData)
			.def_readwrite("pathToClimateDataReorderingFile", &CarbiocialConfiguration::pathToClimateDataReorderingFile);

	def("runCarbiocialSimulation", rcs);
}