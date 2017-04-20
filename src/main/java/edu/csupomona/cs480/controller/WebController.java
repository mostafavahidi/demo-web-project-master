package edu.csupomona.cs480.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import edu.csupomona.cs480.App;
import edu.csupomona.cs480.controller.Storage.FileSystemStorageService;
import edu.csupomona.cs480.controller.Storage.StorageFileNotFoundException;
import edu.csupomona.cs480.controller.Storage.StorageService;
import edu.csupomona.cs480.data.GpsProduct;
import edu.csupomona.cs480.data.User;
import edu.csupomona.cs480.data.provider.GpsProductManager;
import edu.csupomona.cs480.data.provider.UserManager;

/**
 * This is the controller used by Spring framework.
 * <p>
 * The basic function of this controller is to map each HTTP API Path to the
 * correspondent method.
 *
 */

@RestController
public class WebController {

	/**
	 * When the class instance is annotated with {@link Autowired}, it will be
	 * looking for the actual instance from the defined beans.
	 * <p>
	 * In our project, all the beans are defined in the {@link App} class.
	 */
	@Autowired
	private UserManager userManager;

	@Autowired
	private GpsProductManager gpsManager;

	private StorageService storageService = null;

	/**
	 * This is a simple example of how the HTTP API works. It returns a String
	 * "OK" in the HTTP response. To try it, run the web application locally, in
	 * your web browser, type the link: http://localhost:8080/cs480/ping
	 * 
	 * @throws Exception
	 */
	@RequestMapping(value = "/cs480/ping", method = RequestMethod.GET)
	boolean healthCheck(FileSystemStorageService storageService) throws Exception {
		// You can replace this with other string,
		// and run the application locally to check your changes
		// with the URL: http://localhost:8080/
		 AudioAPI test = new AudioAPI();
		 File audioInput = new
		 File("upload.wav");
		 return test.predictor(audioInput);

//		this.storageService = storageService;

//		return true;

	}

	@RequestMapping(value = "/files/{filename:.+}", method = RequestMethod.POST)
	public String listUploadedFiles(Model model) throws IOException {

		model.addAttribute("files",
				storageService.loadAll()
						.map(path -> MvcUriComponentsBuilder
								.fromMethodName(WebController.class, "serveFile", path.getFileName().toString()).build()
								.toString())
						.collect(Collectors.toList()));

		return "uploadForm";
	}

	@RequestMapping(value = "/files2/{filename:.+}", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

		Resource file = storageService.loadAsResource(filename);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
				.body(file);
	}

	@RequestMapping(value= "/", method = RequestMethod.POST)
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {

    	try {
            // copy file
            OutputStream os = new FileOutputStream(new File("upload.wav"));
            IOUtils.copy(file.getInputStream(), os);
            os.close();
            file.getInputStream().close();
        } catch (IOException e) {
        	e.printStackTrace();
        }
		try {
			boolean isAd = healthCheck(null);
			if(isAd) {
				return "This is an ad.";
			} else {
				return "This is a song.";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "error";
	}

	@ExceptionHandler(StorageFileNotFoundException.class)
	public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException exc) {
		return ResponseEntity.notFound().build();
	}

	// RequestMapping(value = "batch/run/add", method = RequestMethod.POST)
	//
	// public void addBatchRun(@RequestParam(value = "description", required =
	// false) String description,
	// @RequestParam("urlFile") MultipartFile file, HttpServletResponse
	// response) throws Exception {
	// String batchId = screenshotManager.addBatchRun(description,
	// file.getInputStream());
	// response.getWriter().write("{ \"id\" : \"" + batchId + "\"}");
	// }

	/**
	 * This is a simple example of how to use a data manager to retrieve the
	 * data and return it as an HTTP response.
	 * <p>
	 * Note, when it returns from the Spring, it will be automatically converted
	 * to JSON format.
	 * <p>
	 * Try it in your web browser: http://localhost:8080/cs480/user/user101
	 */
	@RequestMapping(value = "/cs480/user/{userId}", method = RequestMethod.GET)
	User getUser(@PathVariable("userId") String userId) {
		User user = userManager.getUser(userId);
		return user;
	}

	/**
	 * This is an example of sending an HTTP POST request to update a user's
	 * information (or create the user if not exists before).
	 *
	 * You can test this with a HTTP client by sending
	 * http://localhost:8080/cs480/user/user101 name=John major=CS
	 *
	 * Note, the URL will not work directly in browser, because it is not a GET
	 * request. You need to use a tool such as curl.
	 *
	 * @param id
	 * @param name
	 * @param major
	 * @return
	 */
	@RequestMapping(value = "/cs480/user/{userId}", method = RequestMethod.POST)
	User updateUser(@PathVariable("userId") String id, @RequestParam("name") String name,
			@RequestParam(value = "major", required = false) String major) {
		User user = new User();
		user.setId(id);
		user.setMajor(major);
		user.setName(name);
		userManager.updateUser(user);
		return user;
	}

	/**
	 * This API deletes the user. It uses HTTP DELETE method.
	 *
	 * @param userId
	 */
	@RequestMapping(value = "/cs480/user/{userId}", method = RequestMethod.DELETE)
	void deleteUser(@PathVariable("userId") String userId) {
		userManager.deleteUser(userId);
	}

	/**
	 * This API lists all the users in the current database.
	 *
	 * @return
	 */
	@RequestMapping(value = "/cs480/users/list", method = RequestMethod.GET)
	List<User> listAllUsers() {
		return userManager.listAllUsers();
	}

	@RequestMapping(value = "/cs480/gps/list", method = RequestMethod.GET)
	List<GpsProduct> listAllGpsProducts() {
		return gpsManager.listAllGpsProducts();
	}

	/*********** Web UI Test Utility **********/
	/**
	 * This method provide a simple web UI for you to test the different
	 * functionalities used in this web service.
	 */
//	@RequestMapping(value = "/cs480/home", method = RequestMethod.GET)
//	ModelAndView getUserHomepage() {
//		ModelAndView modelAndView = new ModelAndView("home");
//		modelAndView.addObject("users", listAllUsers());
//		return modelAndView;
//	}

}