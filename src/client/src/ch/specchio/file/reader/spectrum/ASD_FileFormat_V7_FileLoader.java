package ch.specchio.file.reader.spectrum;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import ch.specchio.types.MetaParameter;
import ch.specchio.types.MetaParameterFormatException;
import ch.specchio.types.Metadata;
import ch.specchio.types.SpectralFile;
import ch.specchio.types.spatial_pos;

public class ASD_FileFormat_V7_FileLoader extends SpectralFileLoader {

	// FileInputStream file_input = null;
	// DataInputStream data_in = null;
	// public int file_format_id;
	// String file_version = "not defined";
	// String comments = null;
	// Date[] capture_dates = null;
	// byte program_version = -1;
	// byte dc_corr = -1;
	// byte file_format_version = -1;
	// String dc_corr_comment = null;
	// long dc_time = -1;

	Float[][] reference_data;
	Float[][] base_calibration_data;
	Float[][] lamp_calibration_data;
	Float[][] fibre_optic_data;
	Float[][] dn;
	Float[][] absolute_reflectance_file;

	boolean reference_flag = false;

	byte data_format;

	byte yCode;
	byte yModelType;
	String stitle;
	String sSubTitle;
	String product_name;
	String vendor_name;
	String lot_number;
	String sample_description;
	String model_description;
	String operator_name;
	String date_time;
	String instrument_name;
	String serial_number;
	String display_mode;
	String comments_sample;
	String units_concentration;
	String file_name_sample;
	String user_name;
	int no_of_constituents;
	double[] mDistance;
	double[] mDistanceLimit;
	double[] concentration;
	double[] concentrationLimit;
	double[] fRation;
	double[] residual;
	double[] residualLimit;
	double[] scores;
	double[] scoresLimit;
	long[] modelType;
	double[] reserved1;
	double[] reserved2;

	boolean save_dependent_variables;
	int dependent_variable_count;
	String[] names_dependent_variables;
	float[] dependent_variables;

	byte no_of_cal_buffers;

	int int_time;
	int gain_swir1;
	int gain_swir2;

	byte[] cbType;
	String[] cbName;
	long[] cbIT;
	int[] cbSwir1Gain;
	int[] cbSwir2Gain;

	SpectralFile asd_v7_file;
	
	Metadata smd;

	public ASD_FileFormat_V7_FileLoader() {
		super("ASD Binary");


	}

	public SpectralFile load(File file) throws IOException, MetaParameterFormatException {
		
		 smd = new Metadata();
		
		asd_v7_file = new SpectralFile();
		asd_v7_file.setCaptureDates(new Date[1]); // needed if read_hdr is called
													// directly!
		asd_v7_file.setNumberOfSpectra(2); // one for DN and one for the
											// converted DN values to Radiance
											// or Reflectance		

		asd_v7_file.setAsdV7(true);
		asd_v7_file.setPath(file.getAbsolutePath());
		asd_v7_file.setFilename(file.getName());
		asd_v7_file.setFileFormatName(this.file_format_name);

		String filename = file.getName();

		String[] filename_tokens = filename.split("\\.");

		String spectranumber = filename_tokens[0].substring(filename_tokens[0]
				.length() - 5);

		// spectrum number is contained in the extension for normal ASD files
		try {
			MetaParameter mp = MetaParameter.newInstance(this.attributes_name_hash.get("Spectrum Number"));
			mp.setValue( Integer.valueOf(spectranumber), "RAW");
			smd.add_entry(mp);						

		} catch (NumberFormatException e) {
			// exception: must be a system calibration file
//			asd_v7_file.spectra_numbers[0] = 0;
		}

		asd_v7_file.addSpectrumFilename(asd_v7_file.getFilename());

		file_input = new FileInputStream(file);

		data_in = new DataInputStream(file_input);

		read_ASD_spectrum_file_header(data_in, asd_v7_file);

		// DN values are stored in the first row of the measurements array,
		// Radiance or Reflectance values in the second
		asd_v7_file.setMeasurements(new Float[2][asd_v7_file.getNumberOfChannels(0)]);
		asd_v7_file.setDn(read_data(data_in, asd_v7_file.getNumberOfChannels(0)));
		for (int i = 0; i < asd_v7_file.getNumberOfChannels(0); i++) {
			asd_v7_file.getMeasurements()[0][i] = asd_v7_file.getDn()[0][i]; // the DN
																	// values
																	// are
																	// stored in
																	// the first
																	// row of
																	// the
																	// measurements
																	// array.
		}

		read_reference_file_header(data_in, asd_v7_file);

		reference_data = read_data(data_in, asd_v7_file.getNumberOfChannels(0));		
		
		if (data_in.available() > 0) {
			read_classifier_data(data_in, asd_v7_file);
		}

		if (data_in.available() > 0) {
			read_dependent_variables(data_in, asd_v7_file);
		}

		if (data_in.available() > 0) {
			read_calibration_header(data_in, asd_v7_file);
		}

		if (no_of_cal_buffers > 0 && data_in.available() > 0) {

			// Read Base Calibration Data (Absolute Reflectance or Base File) if
			// reference has not been taken

			for (int i = 0; i < no_of_cal_buffers; i++) {

				if (cbType[i] == 0)
					absolute_reflectance_file = read_data(data_in,
							asd_v7_file.getNumberOfChannels(0));

				if (cbType[i] == 1)
					base_calibration_data = read_data(data_in,
							asd_v7_file.getNumberOfChannels(0));

				// Read Lamp Calibration Data
				if (cbType[i] == 2)
					lamp_calibration_data = read_data(data_in,
							asd_v7_file.getNumberOfChannels(0));

				// Read Fibre Optic Data
				if (cbType[i] == 3)
					fibre_optic_data = read_data(data_in,
							asd_v7_file.getNumberOfChannels(0));

			}

			if (reference_flag == false) {
				asd_v7_file.setAsdV7RadianceFlag(true);
				//asd_v7_file.setAsdV7ReflectanceFlag(true);

				Float[][] radiances = new Float[1][asd_v7_file.getNumberOfChannels(0)];

				if (base_calibration_data != null
						&& lamp_calibration_data != null
						&& fibre_optic_data != null) {
					asd_v7_file.addSpectrumFilename(asd_v7_file.getFilename());
					asd_v7_file.addMeasurementUnits(0, 0); // DN
					asd_v7_file.addMeasurementUnits(1, 2); // Radiance				
					radiances = convert_DN2L(asd_v7_file.getNumberOfChannels(0));
					for (int i = 0; i < asd_v7_file.getNumberOfChannels(0); i++) {
						asd_v7_file.getMeasurements()[1][i] = radiances[0][i]; // the
																			// converted
																			// radiance
																			// values
																			// are
																			// stored
																			// in
																			// the
																			// second
																			// row
																			// of
																			// the
																			// measurements
																			// array.
					}
				}

			}

			if (reference_flag == true) { // routine to convert dn values into
				// reflectance values
				asd_v7_file.setAsdV7RadianceFlag(false);
				asd_v7_file.setAsdV7ReflectanceFlag(true);
				asd_v7_file.addSpectrumFilename(asd_v7_file.getFilename());
				asd_v7_file.addMeasurementUnits(0, 0); // DN
				asd_v7_file.addMeasurementUnits(1, 1); // Reflectance
				

				Float[][] reflectances = convert_DN2R(asd_v7_file.getNumberOfChannels(0));

				if (absolute_reflectance_file != null) {
					for (int i = 0; i < asd_v7_file.getNumberOfChannels(0); i++) {
						asd_v7_file.getMeasurements()[1][i] = reflectances[0][i]
								/ absolute_reflectance_file[0][i];
					}
				} else {
					for (int i = 0; i < asd_v7_file.getNumberOfChannels(0); i++) {
						asd_v7_file.getMeasurements()[1][i] = reflectances[0][i];
					}
				}
			}

		}
		if (no_of_cal_buffers < 1 && reference_flag == true) { // routine to
																// convert dn
																// values into
			// reflectance values
			asd_v7_file.setAsdV7RadianceFlag(false);
			asd_v7_file.setAsdV7ReflectanceFlag(true);
			asd_v7_file.addSpectrumFilename(asd_v7_file.getFilename());
			asd_v7_file.addMeasurementUnits(0, 0); // DN
			asd_v7_file.addMeasurementUnits(1, 1); // Reflectance
			

			Float[][] reflectances = convert_DN2R(asd_v7_file.getNumberOfChannels(0));
			for (int i = 0; i < asd_v7_file.getNumberOfChannels(0); i++) {
				asd_v7_file.getMeasurements()[1][i] = reflectances[0][i];
			}

		}
		
		if (no_of_cal_buffers < 1 && reference_flag == false) { // Just DN's in this file
			
			asd_v7_file.setNumberOfSpectra(1);

			asd_v7_file.setAsdV7RadianceFlag(false);
			asd_v7_file.setAsdV7ReflectanceFlag(false);
		
		}
		else
		{
			// either reflectance or radiance was added
			// therefore, we must add another metadata entry
			asd_v7_file.addEavMetadata(smd);
		}
		

		data_in.close();

		return asd_v7_file;
	}

	public void read_ASD_spectrum_file_header(DataInputStream in,
			SpectralFile hdr) throws IOException, MetaParameterFormatException {

		//hdr.instr_set = new InstrumentSettings();
		//EAVDBServices.getInstance();
				

		// read the company name
		hdr.setCompany("ASD");

		String file_version = read_string(in, 3);
		MetaParameter mp = MetaParameter.newInstance(attributes_name_hash.get("File Version"));
		mp.setValue( file_version, "String");
		smd.add_entry(mp);					

		// comments
		hdr.setComment(read_string(in, 157));

		Character first_char = hdr.getComment().charAt(0);

		if (first_char == 0) {
			hdr.setComment("");
		} else {
			// search for end of string
			int i = 0;
			while (hdr.getComment().charAt(i++) != 0)
				;
			hdr.setComment(hdr.getComment().substring(0, i - 1));
		}

		// date
		Date capture_date = read_asd_time(in);
		hdr.setCaptureDate(0, capture_date);
		hdr.setCaptureDate(1, capture_date);
		// skip till dc_corr flag
		skip(in, 3);

		// flag if dc has been subtracted (1) or not (0)
		byte dc_corr = in.readByte();
		if(dc_corr == 1)
		{
			mp = MetaParameter.newInstance(attributes_name_hash.get("Automatic Dark Current Correction"));
			mp.setValue( "ON", "String");
			smd.add_entry(mp);	
		}
		else
		{
			mp = MetaParameter.newInstance(attributes_name_hash.get("Automatic Dark Current Correction"));
			mp.setValue( "OFF", "String");
			smd.add_entry(mp);				
		}

		// read dc_time: Time of last dc, seconds since 1/1/1970
		long dc_time = this.read_long(in);
		TimeZone tz = TimeZone.getTimeZone("UTC");
		Calendar cal = Calendar.getInstance(tz);
		cal.setTimeInMillis(dc_time * 1000);
//		// cal.setTimeInMillis(0);
//
//		// SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddhhmm");
//		// formatter.setTimeZone(tz);
//
//		// String out=formatter.format(cal.getTime());
//
//		// String timezone = tz.getDisplayName();
//
//		tz = TimeZone.getDefault();
//		long offset = tz.getOffset(cal.getTimeInMillis());
//
//		// tz = TimeZone.getTimeZone("UTC");
//
//		cal.setTime(hdr.capture_dates[0]);
//		// String out2=formatter.format(cal.getTime());
//
//		long measurement_time = cal.getTimeInMillis() / 1000;
//
//		int delta_seconds = (int) (measurement_time - (dc_time + offset / 1000));
//		//hdr.instr_set.add_setting("Time_since_last_dc", delta_seconds);
//		
//		MetaParameter mp = new MetaParameter("Instrument Settings", "");
//		mp.setAttributeName("Time since last DC");
//		mp.setValue(delta_seconds, "s");
//		smd.add_entry(mp);			

		// read data type
		hdr.addMeasurementUnits((int) in.readByte());
		//

		long ref_time = this.read_long(in);
		cal.setTimeInMillis(ref_time * 1000);
		Date wr_time = cal.getTime();

		// skip till data format of spectrum
		skip(in, 8);

		data_format = in.readByte();
		if (data_format < 0 || data_format > 3)
			throw new IOException("Unrecognised data format value " + data_format + " in ASD V7 file.");

		// skip till channels
		skip(in, 4);

		// get number of channels
		hdr.addNumberOfChannels(read_short(in));
		
		// skip till GPS data
		skip(in, 128);

		// read GPS data
		spatial_pos p = read_gps_data(in);
		hdr.addPos(p);
		hdr.addPos(p);

		// integration time in ms
		int_time = this.read_int(in);
		mp = MetaParameter.newInstance(attributes_name_hash.get("Integration Time"));
		mp.setValue( int_time, "ms");
		smd.add_entry(mp);			

		// read foreoptic
		hdr.setForeopticDegrees(read_short(in));

		if (hdr.getForeopticDegrees() == 0)
			hdr.setForeopticDegrees(25); // default for the bare fibre

		// skip dark current correction value
		skip(in, 2);

		// read calibration series number
		hdr.setCalibrationSeries(read_short(in));

		// read instrument number
		hdr.setInstrumentNumber(read_short(in).toString());

		// skip to sample count
		skip(in, 27);

		// read number of samples in the average
		mp = MetaParameter.newInstance(attributes_name_hash.get("Number of internal Scans"));
		mp.setValue(read_short(in), "RAW");		
		smd.add_entry(mp);			

		// read instrument type
		hdr.setInstrumentTypeNumber(in.readByte());

		skip(in, 4); // cal bulb no
		
		gain_swir1 = this.read_short(in);
		gain_swir2 = this.read_short(in);

		mp = MetaParameter.newInstance(attributes_name_hash.get("Gain_SWIR1"));
		mp.setValue( gain_swir1, "RAW");
		smd.add_entry(mp);			
		
		mp = MetaParameter.newInstance(attributes_name_hash.get("Gain_SWIR2"));
		mp.setValue( gain_swir2, "RAW");
		smd.add_entry(mp);	
		
		mp = MetaParameter.newInstance(attributes_name_hash.get("Offset_SWIR1"));
		mp.setValue( this.read_short(in), "RAW");
		smd.add_entry(mp);		
		
		mp = MetaParameter.newInstance(attributes_name_hash.get("Offset_SWIR2"));
		mp.setValue( this.read_short(in), "RAW");
		smd.add_entry(mp);		
		
		
		hdr.addEavMetadata(smd);
		
		// skip to end of header
		skip(in, 40);

	}

	public void read_reference_file_header(DataInputStream in, SpectralFile hdr)
			throws IOException {

		// skip(in, 1); // because first field starts at offset spectrum data
		// size + 1

		// Reference Flag, has reference been taken?
		reference_flag = in.readBoolean();

		skip(in, 1 + 8 + 8);
		skip(in, 2); // random, educated skip
		String useless_description = this.read_string(in,
				this.asd_v7_file.getComment().length());

	}

	public void read_classifier_data(DataInputStream in, SpectralFile hdr)
			throws IOException {

		yCode = in.readByte();

		yModelType = in.readByte();

		skip(in, 2); // random, educated skips

		// read in Title of Classifier
		String temp = "";
		stitle = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			stitle = stitle.concat(temp);
			if (temp.equals("\0") && stitle.charAt(0) == '\0'
					&& stitle.length() < 3) {
				temp = read_string(in, 1);
				stitle.concat(temp);
			}
		}

		// read SubTitle of Classifier
		temp = "";
		sSubTitle = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			sSubTitle = sSubTitle.concat(temp);
			if (temp.equals("\0") && sSubTitle.charAt(0) == '\0'
					&& sSubTitle.length() < 3) {
				temp = read_string(in, 1);
				sSubTitle = sSubTitle.concat(temp);
			}
		}

		// skip(in, 3); // random, educated skip

		// read Product Name
		temp = "";
		product_name = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			product_name = product_name.concat(temp);
			if (temp.equals("\0") && product_name.charAt(0) == '\0'
					&& product_name.length() < 3) {
				temp = read_string(in, 1);
				product_name = product_name.concat(temp);
			}
		}

		// read Vendor Name
		temp = "";
		vendor_name = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			vendor_name = vendor_name.concat(temp);
			if (temp.equals("\0") && vendor_name.charAt(0) == '\0'
					&& vendor_name.length() < 3) {
				temp = read_string(in, 1);
				vendor_name.concat(temp);
			}
		}

		// read LotNumber of Sample
		temp = "";
		lot_number = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			lot_number = lot_number.concat(temp);
			if (temp.equals("\0") && lot_number.charAt(0) == '\0'
					&& lot_number.length() < 3) {
				temp = read_string(in, 1);
				lot_number = lot_number.concat(temp);
			}
		}

		// read Sample Description
		temp = "";
		sample_description = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			sample_description = sample_description.concat(temp);
			if (temp.equals("\0") && sample_description.charAt(0) == '\0'
					&& sample_description.length() < 3) {
				temp = read_string(in, 1);
				sample_description = sample_description.concat(temp);
			}
		}

		// read Model Description
		temp = "";
		model_description = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			model_description = model_description.concat(temp);
			if (temp.equals("\0") && model_description.charAt(0) == '\0'
					&& model_description.length() < 3) {
				temp = read_string(in, 1);
				model_description = model_description.concat(temp);
			}
		}

		// read Operator Name
		temp = "";
		operator_name = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			operator_name = operator_name.concat(temp);
			if (temp.equals("\0") && operator_name.charAt(0) == '\0'
					&& operator_name.length() < 3) {
				temp = read_string(in, 1);
				operator_name = operator_name.concat(temp);
			}
		}

		// read Date, time sample taken
		temp = "";
		date_time = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			date_time = date_time.concat(temp);
			if (temp.equals("\0") && date_time.charAt(0) == '\0'
					&& date_time.length() < 3) {
				temp = read_string(in, 1);
				date_time = date_time.concat(temp);

			}
		}

		// read Instrument Name
		temp = "";
		instrument_name = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			instrument_name = instrument_name.concat(temp);
			if (temp.equals("\0") && instrument_name.charAt(0) == '\0'
					&& instrument_name.length() < 3) {
				temp = read_string(in, 1);
				instrument_name = instrument_name.concat(temp);
			}
		}

		// read serial number of instrument
		temp = "";
		serial_number = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			serial_number = serial_number.concat(temp);
			if (temp.equals("\0") && serial_number.charAt(0) == '\0'
					&& serial_number.length() < 3) {
				temp = read_string(in, 1);
				serial_number = serial_number.concat(temp);
			}
		}

		// read display mode
		temp = "";
		display_mode = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			display_mode = display_mode.concat(temp);
			if (temp.equals("\0") && display_mode.charAt(0) == '\0'
					&& display_mode.length() < 3) {
				temp = read_string(in, 1);
				display_mode = display_mode.concat(temp);
			}
		}

		// read Comments for sample
		temp = "";
		comments_sample = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			comments_sample = comments_sample.concat(temp);
			if (temp.equals("\0") && comments_sample.charAt(0) == '\0'
					&& comments_sample.length() < 3) {
				temp = read_string(in, 1);
				comments_sample = comments_sample.concat(temp);
			}
		}

		// read units of concentration
		temp = "";
		units_concentration = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			units_concentration = units_concentration.concat(temp);
			if (temp.equals("\0") && units_concentration.charAt(0) == '\0'
					&& units_concentration.length() < 3) {
				temp = read_string(in, 1);
				units_concentration = units_concentration.concat(temp);
			}
		}

		// read File name for sample
		temp = "";
		file_name_sample = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			file_name_sample = file_name_sample.concat(temp);
			if (temp.equals("\0") && file_name_sample.charAt(0) == '\0'
					&& file_name_sample.length() < 3) {
				temp = read_string(in, 1);
				file_name_sample = file_name_sample.concat(temp);
			}
		}

		// read User name
		temp = "";
		user_name = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			user_name = user_name.concat(temp);
			if (temp.equals("\0") && user_name.charAt(0) == '\0'
					&& user_name.length() < 3) {
				temp = read_string(in, 1);
				user_name = user_name.concat(temp);
			}
		}

		// read Reservered1
		temp = "";
		String reservered1 = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			reservered1 = reservered1.concat(temp);
			if (temp.equals("\0") && reservered1.charAt(0) == '\0'
					&& reservered1.length() < 3) {
				temp = read_string(in, 1);
				reservered1 = reservered1.concat(temp);
			}
		}

		// read Reservered2
		temp = "";
		String reservered2 = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			reservered2 = reservered2.concat(temp);
			if (temp.equals("\0") && reservered2.charAt(0) == '\0'
					&& reservered2.length() < 3) {
				temp = read_string(in, 1);
				reservered2 = reservered2.concat(temp);
			}
		}

		// read Reservered3
		temp = "";
		String reservered3 = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			reservered3 = reservered3.concat(temp);
			if (temp.equals("\0") && reservered3.charAt(0) == '\0'
					&& reservered3.length() < 3) {
				temp = read_string(in, 1);
				reservered3 = reservered3.concat(temp);
			}
		}

		// read reservered4
		temp = "";
		String reservered4 = "";
		while (!temp.equals("\0")) {
			temp = read_string(in, 1);
			reservered4 = reservered4.concat(temp);
			if (temp.equals("\0") && reservered4.charAt(0) == '\0'
					&& reservered4.length() < 3) {
				temp = read_string(in, 1);
				reservered4 = reservered4.concat(temp);
			}
		}

		// read Number of Constituents
		no_of_constituents = read_short(in);

		int x = 1;

		// TODO: Constituent Type! if number of constituents == 0, no bytes are
		// read!
		if (no_of_constituents != 0) {
			// TODO: read constituents struct... (Size: 96 bytes (double and
			// long plus two (three) strings of variable byte sizes)
			// int length = 200;
			//
			// byte[] test = new byte[length];
			//
			// for (int i = 0; i < test.length; i++){
			// test[i] = in.readByte();
			// }
			//
			// char[] test_char = new char[length];
			// for (int i = 0; i < test.length; i++){
			// test_char[i] = (char) test[i];
			// }
			mDistance = new double[no_of_constituents];
			mDistanceLimit = new double[no_of_constituents];
			concentration = new double[no_of_constituents];
			concentrationLimit = new double[no_of_constituents];
			fRation = new double[no_of_constituents];
			residual = new double[no_of_constituents];
			residualLimit = new double[no_of_constituents];
			scores = new double[no_of_constituents];
			scoresLimit = new double[no_of_constituents];
			modelType = new long[no_of_constituents];
			reserved1 = new double[no_of_constituents];
			reserved2 = new double[no_of_constituents];

			for (int i = 0; i < no_of_constituents; i++) {
				skip(in, 42);
				mDistance[i] = read_double(in);
				mDistanceLimit[i] = read_double(in);
				concentration[i] = read_double(in);
				concentrationLimit[i] = read_double(in);
				fRation[i] = read_double(in);
				residual[i] = read_double(in);
				residualLimit[i] = read_double(in);
				scores[i] = read_double(in);
				scoresLimit[i] = read_double(in);
				modelType[i] = read_long(in);
				reserved1[i] = read_double(in);
				reserved2[i] = read_double(in);
			}
		}

		// skip(in, 21); // random, educated skip

	}

	public void read_dependent_variables(DataInputStream in, SpectralFile hdr)
			throws IOException {

		// skip(in,1);
		// int dep_test = read_short(in);
		//
		// int length = 100;
		//
		// byte[] test = new byte[length];
		//
		// for (int i = 0; i < test.length; i++){
		// test[i] = in.readByte();
		// }
		//
		// char[] test_char = new char[length];
		// for (int i = 0; i < test.length; i++){
		// test_char[i] = (char) test[i];
		// }

		// Has reference been taken?

		// skip(in, 13); // random skip for testing

		save_dependent_variables = in.readBoolean();

		// Number of dependent variables
		dependent_variable_count = read_short(in);

		// skip(in, 1);

		names_dependent_variables = new String[dependent_variable_count];
		dependent_variables = new float[dependent_variable_count];

		// Read Names of dependent variables

		if (dependent_variable_count == 0) {
			names_dependent_variables = new String[1];
			dependent_variables = new float[1];
			String temp = "";
			names_dependent_variables[0] = "";
			while (!temp.equals("\0")) {
				temp = read_string(in, 1);
				names_dependent_variables[0] = names_dependent_variables[0]
						.concat(temp);
			}

			dependent_variables[0] = read_float(in);

		} else {
			for (int i = 0; i < dependent_variable_count; i++) {
				String temp = "";
				names_dependent_variables[i] = "";
				while (!temp.equals("\0")) {
					temp = read_string(in, 1);
					names_dependent_variables[i] = names_dependent_variables[i]
							.concat(temp);
				}
			}
			// Not sure if there is one float value per dependent variable (which would be logical) or if there
			// is just one float value stored in this field, which is suggested by the file format structure sheet
			// provided by ASD
			
			// Case where there are as many variables read in, as there are dependent variables
//			for (int i = 0; i < dependent_variable_count; i++) {
//				dependent_variables[i] = read_float(in);
//			}
			
			//Case where there is just one variable read:
			dependent_variables[0] = read_float(in);
			
		}

		int x = 1;

	}

	public void read_calibration_header(DataInputStream in, SpectralFile hdr)
			throws IOException {

//		int length = 100;

		// byte[] test = new byte[length];
		//
		// for (int i = 0; i < test.length; i++){
		// test[i] = in.readByte();
		// }
		//
		// char[] test_char = new char[length];
		// for (int i = 0; i < test.length; i++){
		// test_char[i] = (char) test[i];
		// }

		// Number of calibration buffers in the file
		no_of_cal_buffers = in.readByte();

		cbType = new byte[no_of_cal_buffers];
		cbName = new String[no_of_cal_buffers];
		cbIT = new long[no_of_cal_buffers];
		cbSwir1Gain = new int[no_of_cal_buffers];
		cbSwir2Gain = new int[no_of_cal_buffers];

		for (int i = 0; i < no_of_cal_buffers; i++) {
			cbType[i] = in.readByte(); // if ABS(0), BSE(1), LMP (2), or FO(3)
			cbName[i] = read_string(in, 20); // read Name of file
			cbIT[i] = read_long(in); // Integration Time in ms of buffer
			cbSwir1Gain[i] = read_short(in); // Swir1 Gain of buffer
			cbSwir2Gain[i] = read_short(in); // Swir2 Gain of buffer
		}

		// Read in structure for each calibration buffer
		// byte cbType = in.readByte(); // if ABS(0), BSE(1), LMP (2), or FO(3)
		//
		// String cbName = read_string(in, 20); // read Name of file

		// long cbIT = read_long(in); // Integration Time in ms of buffer
		//
		// short cbSwir1Gain = read_short(in); // Swir1 Gain of buffer
		//
		// short cbSwir2Gain = read_short(in); // Swir2 Gain of buffer

//		 int length = in.available();
//		
//		 byte[] test = new byte[length];
//		
//		 for (int i = 0; i < test.length; i++){
//		 test[i] = in.readByte();
//		 }
//		
//		 char[] test_char = new char[length];
//		 for (int i = 0; i < test.length; i++){
//		 test_char[i] = (char) test[i];
//		 }
//		
//		 long[] test_long = new long[length];
//		 for (int i = 0; i < test.length; i++){
//		 test_long[i] = (long) test[i];
//		 }
//		
//		 int[] test_int = new int[length];
//		 for (int i = 0; i < test.length; i++){
//		 test_int[i] = (int) test[i];
//		 }

		// for (int i = 0; i < test.length - 3; i++){
		//
		// if(test_char[i] == 'b' && test_char[i+1] == 's' && test_char[i+2] ==
		// 'e')
		// {
		// int x = 0;
		// }
		//
		//
		// }

		int x = 1;

		//
	}

	public Float[][] convert_DN2L(int channels) {

		float[] gain_v = new float[3];
		int vnir_top = 1000 - 350 + 1;
		int swir_1_top = vnir_top + 800;
		Float[][] cal_rad = new Float[1][channels];
		Float[][] s = new Float[1][channels];
		Float[][] l = new Float[1][channels];

		gain_v[0] = cbIT[2];
		gain_v[1] = (float) 2048 / (float) cbSwir1Gain[2];
		gain_v[2] = (float) 2048 / (float) cbSwir2Gain[2];

		for (int i = 0; i < channels; i++) {
			cal_rad[0][i] = lamp_calibration_data[0][i] / (float) Math.PI
					* base_calibration_data[0][i];
		}

		for (int i = 0; i < vnir_top; i++) {
			s[0][i] = cal_rad[0][i] / (fibre_optic_data[0][i] / gain_v[0]);
		}

		for (int i = vnir_top; i < swir_1_top; i++) {
			s[0][i] = cal_rad[0][i] / (fibre_optic_data[0][i] / gain_v[1]);
		}

		for (int i = swir_1_top; i < channels; i++) {
			s[0][i] = cal_rad[0][i] / (fibre_optic_data[0][i] / gain_v[2]);
		}

		gain_v[0] = int_time;
		gain_v[1] = (float) 2048 / gain_swir1;
		gain_v[2] = (float) 2048 / (float) gain_swir2;

		for (int i = 0; i < vnir_top; i++) {
			l[0][i] = s[0][i] * asd_v7_file.getDn()[0][i] / gain_v[0];
		}

		for (int i = vnir_top; i < swir_1_top; i++) {
			l[0][i] = s[0][i] * asd_v7_file.getDn()[0][i] / gain_v[1];
		}

		for (int i = swir_1_top; i < channels; i++) {
			l[0][i] = s[0][i] * asd_v7_file.getDn()[0][i] / gain_v[2];
		}

		return l;

	}

	public Float[][] convert_DN2R(int channels) {
		Float[][] refl = new Float[1][channels];
		for (int i = 0; i < channels; i++) {
			refl[0][i] = asd_v7_file.getDn()[0][i] / reference_data[0][i];
		}

		return refl;

	}

	Float[][] read_data(DataInputStream in, int channels) throws IOException {
		Float[][] f = new Float[1][channels];
		if (data_format == 0) {
			for (int i = 0; i < channels; i++) {
				f[0][i] = read_float(in);
			}
		}
		if (data_format == 1) {
			for (int i = 0; i < channels; i++) {
				f[0][i] = read_int(in).floatValue();
			}
		}
		if (data_format == 2) {
			for (int i = 0; i < channels; i++) {
				f[0][i] = read_double(in).floatValue();
			}
		}
		if (data_format == 3) {
			for (int i = 0; i < channels; i++) {
				f[0][i] = read_float(in);
			}
		}
		return f;
	}

	Date read_asd_time(DataInputStream in) throws IOException {
		
		TimeZone tz = TimeZone.getDefault();
		Calendar cal = Calendar.getInstance(tz);

		Integer sec = read_short(in);
		Integer min = read_short(in);
		Integer hour = read_short(in);
		Integer mday = read_short(in);
		Integer month = read_short(in); // months start at 0 in the ASD
										// structure
		Integer year = read_short(in) + 1900;

		// skip three shorts
		skip(in, 6);

		// month starts at 0: this conforms with the java calendar class!
		cal.set(year, month, mday, hour, min, sec);

		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddhhmm");
		formatter.setTimeZone(tz);

		return cal.getTime();
	}

	spatial_pos read_gps_data(DataInputStream in) throws IOException {
		spatial_pos pos = null;

		// skip true heading and speed
		skip(in, 16);

		double lat = read_double(in);
		double lon = read_double(in);
		double alt = read_double(in);

		// // reformat to dd.mmmmmmmmm
		// int lat_deg = (int)lat/100;
		// double lat_min = (lat - lat_deg*100)/60;
		//
		// int lon_deg = (int)lon/100;
		// double lon_min = (lon - lon_deg*100)/60;

		// only create position record if the position is not zero
		if (lat != 0 && lon != 0 && alt != 0) {
			pos = new spatial_pos();
			// pos.latitude = lat_deg + lat_min;
			// pos.longitude = lon_deg + lon_min;
			// pos.altitude = alt;
			pos.latitude = asd_v7_file.DDDmm2DDDdecimals(lat);
			pos.longitude = asd_v7_file.DDDmm2DDDdecimals(lon);
			pos.altitude = alt;
		}

		// skip rest
		skip(in, 16);

		return pos;
	}

	protected String read_string(DataInputStream in, int no_of_chars)
			throws IOException {
		byte[] bytes = new byte[no_of_chars];
		if (in.read(bytes) == -1)
			throw new EOFException();

		// add null at the end
		// byte[] bytes_ = new byte[no_of_chars+1];
		//
		// for (int i=0;i<no_of_chars;i++) bytes_[i] = bytes[i];
		// bytes_[no_of_chars] = 0;
		//

		return new String(bytes);
	}

	protected Integer read_short(DataInputStream in) throws IOException {
		byte[] b = new byte[2];
		if (in.read(b) == -1)
			throw new EOFException();
		return (new Integer(arr2int(b, 0)));
	}

	protected Integer read_int(DataInputStream in) throws IOException {
		byte[] b = new byte[4];
		if (in.read(b) == -1)
			throw new EOFException();
		Integer n = arr4int(b, 0); // strange why reading just an integer wont
									// work (uint not existing in Java???)
		return n;
	}

	protected Integer read_uint(DataInputStream in) throws IOException {
		byte[] b = new byte[4];
		if (in.read(b) == -1)
			throw new EOFException();
		Integer n = (int) arr4uint(b, 0); // strange why reading just an integer
											// wont work (uint not existing in
											// Java???)
		return n;
	}

	protected Integer read_long(DataInputStream in) throws IOException {
		byte[] b = new byte[4];
		if (in.read(b) == -1)
			throw new EOFException();
		long n = arr2long(b, 0);

		int as_int = (int) n;

		return as_int;
	}

	protected Float read_float(DataInputStream in) throws IOException {
		byte[] b = new byte[4];
		if (in.read(b) == -1)
			throw new EOFException();
		return (new Float(arr2float(b, 0)));
	}

	protected Double read_double(DataInputStream in) throws IOException {
		byte[] b = new byte[8];
		if (in.read(b) == -1)
			throw new EOFException();
		return (new Double(arr2double(b, 0)));
	}

	protected void skip(DataInputStream in, int no_of_bytes) {
		try {
			in.skipBytes(no_of_bytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void whitespace(BufferedReader d) {
		try {

			// mark
			d.mark(10);

			char c = (char) d.read();

			while (c == ' ' || c == '\t') {
				d.mark(10);
				c = (char) d.read();
			}

			d.reset();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * binary to double, float, int and short taken from
	 * http://www.captain.at/howto-java-convert-binary-data.php
	 */

	public static double arr2double(byte[] arr, int start) {
		int i = 0;
		int len = 8;
		int cnt = 0;
		byte[] tmp = new byte[len];
		for (i = start; i < (start + len); i++) {
			tmp[cnt] = arr[i];
			// System.out.println(java.lang.Byte.toString(arr[i]) + " " + i);
			cnt++;
		}
		long accum = 0;
		i = 0;
		for (int shiftBy = 0; shiftBy < 64; shiftBy += 8) {
			accum |= ((long) (tmp[i] & 0xff)) << shiftBy;
			i++;
		}
		return Double.longBitsToDouble(accum);
	}

	public static float arr2float(byte[] arr, int start) {
		int i = 0;
		int len = 4;
		int cnt = 0;
		byte[] tmp = new byte[len];
		for (i = start; i < (start + len); i++) {
			tmp[cnt] = arr[i];
			cnt++;
		}
		int accum = 0;
		i = 0;
		for (int shiftBy = 0; shiftBy < 32; shiftBy += 8) {
			accum |= ((long) (tmp[i] & 0xff)) << shiftBy;
			i++;
		}
		return Float.intBitsToFloat(accum);
	}

	public static long arr2long(byte[] arr, int start) {
		int i = 0;
		int len = 4;
		int cnt = 0;
		byte[] tmp = new byte[len];
		for (i = start; i < (start + len); i++) {
			tmp[cnt] = arr[i];
			cnt++;
		}
		long accum = 0;
		i = 0;
		for (int shiftBy = 0; shiftBy < 32; shiftBy += 8) {
			accum |= ((long) (tmp[i] & 0xff)) << shiftBy;
			i++;
		}
		return accum;
	}

	public static int arr2int(byte[] arr, int start) {
		int low = arr[start] & 0xff;
		int high = arr[start + 1] & 0xff;
		return (int) (high << 8 | low);
	}

	public static int arr4int(byte[] arr, int start) {
		int b1 = arr[start] & 0xff;
		int b2 = arr[start + 1] & 0xff;
		int b3 = arr[start + 1] & 0xff;
		int b4 = arr[start + 1] & 0xff;
		return (int) (b4 << 24 | b3 << 16 | b2 << 8 | b1);
	}

	public static long arr4uint(byte[] by, int start) {

		long value = 0;
		for (int i = 0; i < by.length; i++) {
			value += (by[i] & 0xff) << (8 * i);
		}

		return value;
	}

}
