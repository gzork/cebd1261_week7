package com.cellariot.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._
import scala.io.Source
import java.nio.charset.CodingErrorAction
import scala.io.Codec

/** Find the movies with the most ratings. */
object PopularMovies_assignment {

  /** Load up a Map of movie IDs to movie names. */
  def loadMovieNames(): Map[Int, String] = {

    // Handle character encoding issues:
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Create a Map of Ints to Strings, and populate it from u.item.
    var movieNames: Map[Int, String] = Map()

    val lines = Source.fromFile("../ml-100k/u.item").getLines()
    for (line <- lines) {
      var fields = line.split('|')
      if (fields.length > 1) {
        movieNames += (fields(0).toInt -> fields(1))
      }
    }

    return movieNames
  }

  /** Our main function where the action happens */
  def main(args: Array[String]) {

    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)

    val conf = new  SparkConf().setMaster("local[*]").setAppName("PopularMovies_v2").set("spark.driver.host", "localhost");
    // Create a SparkContext using every core of the local machine, named PopularMovies_v2
    //alternative: val sc = new SparkContext("local[*]", "PopularMovies_v2")
    val sc = new SparkContext(conf) 

    // Create a broadcast variable of our ID -> movie name map
    var nameDict = sc.broadcast(loadMovieNames)

    // Read in each rating line
    val lines = sc.textFile("../ml-100k/u.data")

    // Map to (movieID, (rating, 1)) tuples
    val movies = lines.map(x => (x.split("\t")(1).toInt, (x.split("\t")(2).toFloat, 1)))

    // Count the average rating, and all the 1's for each movie, then filter the ones where the count is higher than 200
    val movieCounts = movies.reduceByKey((x, y) => ((x._1 + y._1)/2, x._2 + y._2)).filter(_._2._2 > 200)
    
    // Flip (movieID, (avgRating, count)) to (avgRating, movieID)
    val flipped = movieCounts.map(x => (x._2._1, x._1))

    // Sort by avgRating
    val sortedMovies = flipped.sortByKey(false)

    // Fold in the movie names from the broadcast variable
    val sortedMoviesWithNames = sortedMovies.map(x => (nameDict.value(x._2), x._1))

    // Collect and print results, the top 10 rated movies with more than 200 ratings
    val results = sortedMoviesWithNames.collect().take(10)

    results.foreach(println)
  }

}

