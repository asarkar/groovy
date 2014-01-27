package name.abhijitsarkar.moviemanager.service

import groovy.transform.PackageScope

import java.util.regex.Pattern

import javax.annotation.ManagedBean
import javax.annotation.PostConstruct
import javax.inject.Inject

import name.abhijitsarkar.moviemanager.annotation.GenreList
import name.abhijitsarkar.moviemanager.domain.Movie
import name.abhijitsarkar.moviemanager.domain.MovieRip

import org.apache.log4j.Logger

@ManagedBean
class MovieRipService {
	/*
	 * The following regex matches file names with release year in parentheses,
	 * something like Titanic (1997).mkv Each part of the regex is explained
	 * further:
	 * 
	 * ([-',!\\[\\]\\.\\w\\s]++) -> Matches one or more occurrences of any
	 * alphabet, number or the following special characters in the movie name:
	 * dash (-), apostrophe ('), comma (,), exclamation sign (!), square braces
	 * ([]), full stop (.)
	 * 
	 * (?:\\((\\d{4})\\)) -> Matches 4 digit release year within parentheses.
	 * 
	 * (.++) -> Matches one or more occurrences of any character.
	 */
	private static final MOVIE_NAME_WITH_RELEASE_YEAR_REGEX = "([-',!\\[\\]\\.\\w\\s]++)(?:\\((\\d{4})\\))?+(.++)"
	private static final pattern = Pattern.compile(MOVIE_NAME_WITH_RELEASE_YEAR_REGEX)
	private static logger = Logger.getInstance(MovieRipService.class)

	private genreList

	// For CDI to work, the injection point must be strongly typed
	@Inject
	MovieRipService(@GenreList List<String> genreList) {
		this.genreList = genreList
	}

	@PostConstruct
	void postConstruct() {
		assert genreList : 'Genre list must not be null.'
	}

	def getMovieRips(movieDirectory) throws IOException {
		def f = new File(movieDirectory)

		if (!f.isAbsolute()) {
			logger.warn("Path ${movieDirectory} is not absolute: it's resolved to ${movieDirectory.absolutePath}")
		}

		def movieRips = new TreeSet<MovieRip>()

		addToMovieRips(f, movieRips)

		movieRips
	}

	@PackageScope
	def addToMovieRips(rootDir, movieRips, currentGenre = null) {
		if (!rootDir.exists() || !rootDir.isDirectory()) {
			throw new IllegalArgumentException("${rootDir.canonicalPath} does not exist or is not a directory.")
		}
		if (!rootDir.canRead()) {
			throw new IllegalArgumentException("${rootDir.canonicalPath} does not exist or is not readable.")
		}

		rootDir.eachFileRecurse { File f ->
			delegate = this

			if (f.isDirectory() && isGenre(f.name)) {
				currentGenre = f.name
			} else if (isMovieRip(f.name)) {
				def movieRip = parseMovieRip(f.name)

				movieRip.genres = currentGenre as List
				movieRip.fileSize = f.length()

				def parent = getParent(f, currentGenre, rootDir)

				if (!currentGenre?.equalsIgnoreCase(parent)) {
					movieRip.parent = parent
				}

				logger.info("Found movie: ${movieRip}")

				boolean isUnique = movieRips.add(movieRip)

				if (!isUnique) {
					logger.warn("Found duplicate movie: ${movieRip}")
				}
			}
		}
	}

	@PackageScope
	def parseMovieRip(fileName) {
		def movieTitle
		def fullStop = '.'
		def movieRipFileExtension = getFileExtension(fileName)
		def lastPart

		def year = 0
		def imdbRating = -1.0f

		def matcher = pattern.matcher(fileName)
		if (matcher.find() && matcher.groupCount() >= 1) {
			// 1st group is the title, always present
			logger.debug("matcher.group(1): ${matcher.group(1)}")

			movieTitle = matcher.group(1).trim()

			// If present, the 2nd group is the release year
			logger.debug("matcher.group(2): ${matcher.group(2)}")
			year = matcher.group(2) ? Integer.parseInt(matcher.group(2)) : year

			// If present, the 3rd group might be one of 2 things:
			// 1) The file extension
			// 2) A "qualifier" to the name like "part 1" and the file extension
			logger.debug("matcher.group(3): ${matcher.group(3)}")
			lastPart = matcher.group(3) ?: null

			if (lastPart && (lastPart != movieRipFileExtension)) {
				// Extract the qualifier
				movieTitle += lastPart.substring(0, lastPart.length()
						- (movieRipFileExtension.length() + 1))
			}
		} else {
			logger.debug("Found unconventional filename: ${fileName}")
			// Couldn't parse file name, extract as-is without file extension
			movieTitle = fileName.substring(0, fileName.length()
					- (movieRipFileExtension.length() + 1))
		}

		def m = new Movie(title: movieTitle, imdbRating: imdbRating,
		releaseDate: Date.parse('MM/dd/yyyy', "01/01/${year}"))

		def mr = new MovieRip(m)
		mr.fileExtension = "${fullStop}${movieRipFileExtension}"

		mr
	}

	@PackageScope
	def isMovieRip(fileName) {
		try {
			MovieRipFileFormat.valueOf(MovieRipFileFormat.class,
					getFileExtension(fileName).toUpperCase())
		} catch (IllegalArgumentException iae) {
			return false
		}
		true
	}

	@PackageScope
	def isGenre(fileName) {
		genreList.contains(fileName)
	}

	@PackageScope
	def getFileExtension(fileName) {
		/* Unicode representation of char . */
		def fullStop = '.'
		def fullStopIndex = fileName.lastIndexOf(fullStop)

		if (fullStopIndex < 0) {
			return ''
		}

		fileName.substring(++fullStopIndex, fileName.length())
	}

	@PackageScope
	def getParent(file, currentGenre, rootDirectory, immediateParent = null) {
		def parentFile = file.parentFile

		if (!parentFile?.isDirectory() || parentFile?.compareTo(rootDirectory) <= 0) {
			return immediateParent
		}

		if (parentFile.name.equalsIgnoreCase(currentGenre)) {
			if (file.isDirectory()) {
				return file.name
			}
		}

		getParent(parentFile, currentGenre, rootDirectory, parentFile.name)
	}

	private enum MovieRipFileFormat {
		AVI,  MKV,  MP4,  DIVX, MOV
	}
}