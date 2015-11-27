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

#ifndef _CARBIOCIAL_H_
#define _CARBIOCIAL_H_

#include <vector>
#include <map>
//#include <set>

#include "core/monica-parameters.h"
#include "soil/soil.h"

namespace Carbiocial
{
	Soil::SoilPMsPtr carbiocialSoilParameters(int profileId, 
																						int layerThicknessCm,
																						int maxDepthCm,
																						std::string output_path,
																						Monica::CentralParameterProvider cpp);

	class CarbiocialConfiguration
	{
	public:
		CarbiocialConfiguration() {}
		~CarbiocialConfiguration() {}

		bool writeOutputFiles{false};
		bool create2013To2040ClimateData{false};
		std::string pathToClimateDataReorderingFile;

		std::string getClimateFile() const  { return climate_file; }
		std::string getPathToIniFile() const { return pathToIniFile; }
		std::string getInputPath() const { return input_path; }
		std::string getOutputPath() const { return output_path; }
		Tools::Date getStartDate() const { return start_date; }
		Tools::Date getEndDate() const { return end_date; }
		int getRowId() const { return row_id; }
		int getColId() const { return col_id; }
		double getLatitude() const { return latitude; }
		double getElevation() const { return elevation; }
		int getProfileId() const { return profileId; }

		void setClimateFile(std::string climate_file) { this->climate_file = climate_file; }
		//void setIniFile(std::string ini_file) { this->ini_file = ini_file; }
		void setPathToIniFile(std::string pathToIniFile) { this->pathToIniFile = pathToIniFile; }
		void setInputPath(std::string path) { this->input_path = path; }
		void setOutputPath(std::string path) { this->output_path = path; }
		void setStartDate(std::string date) { this->start_date = Tools::fromMysqlString(date.c_str()); }
		void setEndDate(std::string date) { this->end_date = Tools::fromMysqlString(date.c_str()); }
		void setRowId(int row_id) { this->row_id = row_id; }
		void setColId(int col_id) { this->col_id = col_id; }
		void setLatitude(double lat) { this->latitude = lat; }
		void setElevation(double ele) { this->elevation = ele; }
		void setProfileId(int pid) { profileId = pid; }

	private:
		std::string climate_file;
		//std::string ini_file;
		std::string pathToIniFile;
		std::string input_path;
		std::string output_path;
		Tools::Date start_date;
		Tools::Date end_date;
		int row_id{0};
		int col_id{0};
		double latitude{-9.41};
		double elevation{300.0};
		int profileId{-1};
	};

	std::map<int, double> 
		runCarbiocialSimulation(const CarbiocialConfiguration* simulation_config = 0);
	
	Climate::DataAccessor 
		climateDataFromCarbiocialFiles(const std::string& pathToFile, 
																	 const Monica::CentralParameterProvider& cpp,
																	 double latitude, 
																	 const CarbiocialConfiguration* simulation_config);
}

#endif //_CARBIOCIAL_H_
