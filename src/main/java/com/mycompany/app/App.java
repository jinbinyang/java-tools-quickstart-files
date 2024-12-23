package com.mycompany.app;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.jacksonstore.MultiFormatStore.Verbose;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxConstants;
import org.spdx.library.Version;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxFile;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.storage.IModelStore;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        ISerializableModelStore store = new MultiFormatStore(	// This will "store" the deserialized JSON file
        		new InMemSpdxStore(),	// Store the JSON file in memory - the parameter allows for other, more robust, storage
				Format.JSON_PRETTY,		// Specify the JSON format - this store also supports XML and YAML
				Verbose.COMPACT);		// Specify how verbose when serializing
        String documentUri = null;		// The Document URI is a unique key for every deserialized SPDX document in the store
        try (InputStream is = new FileInputStream("SPDXJSONExample-v2.3.spdx.json")) {
        	documentUri = store.deSerialize(is, false);	// This deserializes the JSON file - it returns the document URI
		} catch (IOException e) {
			System.out.println("I/O error reading the SPDX Json file");
			System.exit(-1);
		} catch (InvalidSPDXAnalysisException e) {
			System.out.println("Error parsing the SPDX JSON file: "+e.getMessage());
			System.exit(-1);
		}
        try {
			SpdxDocument doc = new SpdxDocument(	// the SPDX document is a model object representing the serialized SPDX content in the file
					store,							// the store where we deserialized the JSON file
					documentUri,					// the URI of the document we wish to access
					null,							// an optional copy manager can be provided if working with more than one store
					false);							// we the document already exists, we don't want to create it
			System.out.println("Successfully deserialized " + documentUri);
			System.out.println("Following are the creators:");
			for (String creator:doc.getCreationInfo().getCreators()) {
				System.out.println("\t"+creator);
			}
			
			IModelStore inMemStore = new InMemSpdxStore();
			String newDocumentUri = "http://spdx.org/spdxdocs/spdx-example2-444504E0-4F89-41D3-9A0C-0305E82CCCCC";
			SpdxDocument myDoc = new SpdxDocument(
					inMemStore,						// here we'll just use a very simple in memory store which doesn't support serialization
					newDocumentUri,					// the URI of the document - must be globally unique
					null,							// an optional copy manager can be provided if working with more than one store
					true);							// this time, we want to create it
			System.out.println("Successfully created " + newDocumentUri);
			
			myDoc.setCreationInfo(				// Set the required creationInfo
					myDoc.createCreationInfo(	// All model objects have a set of convenience methods to create 
												// other model objects using the same model store and document URI
							Arrays.asList(new String[] {"Tool: Sample App"}), // creators
							new SimpleDateFormat(SpdxConstants.SPDX_DATE_FORMAT).format(new Date())));
												// creation date - note that SpdxConstants has several useful constant values
			myDoc.setSpecVersion(Version.CURRENT_SPDX_VERSION); // the Version class has constants defined for all supported SPDX spec versions
			myDoc.setName("My Document");
			// The LicenseInfoFactory contains some convenient static methods to manage licenses including
			// a license parser.  Note that we have to pass in the model store and document URI so that the
			// license is created in the same store.
			myDoc.setDataLicense(LicenseInfoFactory.parseSPDXLicenseString("CC0-1.0", inMemStore, newDocumentUri, null));
			// We need something for the document to describe, we'll create an SPDX file
			AnyLicenseInfo apacheLicense = LicenseInfoFactory.parseSPDXLicenseString("Apache-2.0", inMemStore, newDocumentUri, null);
			SpdxFile file = myDoc.createSpdxFile(
					SpdxConstants.SPDX_ELEMENT_REF_PRENUM + "44",
					"./myfile/name", 
					apacheLicense, 
					Arrays.asList(new AnyLicenseInfo[] {apacheLicense}),
					"Copyright me, 2023",
					myDoc.createChecksum(ChecksumAlgorithm.SHA1, "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12"))
					.build();  // The more complex model objects follows a builder pattern
			myDoc.getDocumentDescribes().add(file);
			
			List<String> warnings = myDoc.verify();
			if (warnings.isEmpty()) {
				System.out.println("My doc is valid");
			} else {
				System.out.println("Verification failed for the following reason(s):");
				for (String warning:warnings) {
					System.out.println("\t"+warning);
				}
			}
			
			try (OutputStream os = new FileOutputStream("SPDXJSONExample-v2.3-copy.spdx.json")) {
	        	store.serialize(documentUri, os);
			} catch (IOException e) {
				System.out.println("I/O error writing the SPDX Json file");
				System.exit(-1);
			} catch (InvalidSPDXAnalysisException e) {
				System.out.println("Error parsing the SPDX JSON file: "+e.getMessage());
				System.exit(-1);
			}
			System.out.println("Serialized SPDX document to SPDXJSONExample-v2.3-copy.spdx.json");
			
			ModelCopyManager copyManager = new ModelCopyManager();  // the copy manager handles copying between model store
			myDoc.setCopyManager(copyManager); 	// we need to set the copymanager in the document we're copying from
			ISerializableModelStore storeForSerialization = new MultiFormatStore(new InMemSpdxStore(),
					Format.JSON_PRETTY,	Verbose.COMPACT);			// A new model store just to serialize the JSON file
			SpdxDocument docToSerialize = new SpdxDocument(
					storeForSerialization,			// a model store that supports serialization
					newDocumentUri,					// we'll use the same URI as myDoc since we're making a copy
					copyManager,					// the copy manager must be the same as the one we set in myDoc
					true);
			docToSerialize.copyFrom(myDoc);			// This will copy the document to the new model store
			
			try (OutputStream os = new FileOutputStream("my-doc.spdx.json")) {
				storeForSerialization.serialize(newDocumentUri, os);
			} catch (IOException e) {
				System.out.println("I/O error writing the SPDX Json file");
				System.exit(-1);
			} catch (InvalidSPDXAnalysisException e) {
				System.out.println("Error parsing the SPDX JSON file: "+e.getMessage());
				System.exit(-1);
			}
			System.out.println("Serialized SPDX document to my-doc.spdx.json");
		} catch (InvalidSPDXAnalysisException e) {
			System.out.println("Error accessing SPDX document: "+e.getMessage());
			System.exit(-1);
		}
    }
}
