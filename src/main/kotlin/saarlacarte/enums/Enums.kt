package saarlacarte.enums

/**
 Type of a restaurant
 **/
enum class RestaurantType { EUROPEAN, ASIAN, AFRICAN, AMERICAN }

/**
 Type of a table
 **/
enum class TableType { COMMON, BAR, SEPARATED }

/**
 Type of a cook
 **/
enum class CookType { EXEC, SOUS, TOURNANT, SAUCE, FISH, ROAST, VEGETABLE, PASTRY }

/**
 Unit an ingredient is measured in
 **/
enum class MeasuringUnit { G, X, ML }

/**
 State of a delivery driver
 **/
enum class DriverState { FREE, DELIVERING, RETURNING }

/**
 Action performed by a waiter
 **/
enum class WaiterAction { SEAT, TAKING_ORDER, SERVING, ESCORT }

/**
 Status of a table
 **/
enum class TableStatus { FREE, RESERVED, OCCUPIED }

/**
 Log level for the simulation logger
 **/
enum class LogLevel { DEBUG, INFO, IMPORTANT }

/**
 Type of restaurant staff affected by an incident
 **/
enum class StaffType { COOK, WAITSTAFF, DRIVER }

/**
 How likely a customer group is to leave a rating
 **/
enum class RatingLikelihood { NEVER, SOME, ALWAYS }

/**
 Status of a dish within an order
 **/
enum class DishStatus { NOTSTARTED, INPROGRESS, FINISHED, SERVED }

/**
 Overall experience of a customer group
 **/
enum class CustomerExperience { POSITIVE, NEUTRAL, NEGATIVE }
