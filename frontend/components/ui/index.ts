/**
 * Barrel export for the UI primitives.
 *
 * Importing from `@/components/ui` (rather than each file) keeps call sites
 * short and gives us one place to audit the public primitive surface.
 */
export { cn } from "@/lib/utils";
export { Button, buttonClasses, type ButtonVariant, type ButtonSize } from "./button";
export {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardBody,
  CardFooter,
} from "./card";
export { Input, Textarea, Select } from "./input";
export { Label } from "./label";
export { FieldError } from "./field-error";
export {
  Badge,
  BookingStatusBadge,
  ServiceStatusBadge,
  VendorStatusBadge,
  type BadgeTone,
} from "./badge";
export { Container, PageHeader } from "./container";
export { Spinner } from "./spinner";
export { StarRating } from "./star-rating";
