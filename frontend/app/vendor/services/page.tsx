"use client";

/**
 * Vendor — service management (`/vendor/services`).
 *
 * The backend's five vendor-service endpoints power this single page:
 *   - GET    /api/vendor/services?page&size            (list, ALL statuses)
 *   - POST   /api/vendor/services                      (create → DRAFT)
 *   - PUT    /api/vendor/services/{id}                 (update fields)
 *   - POST   /api/vendor/services/{id}/activate        (→ ACTIVE, published)
 *   - DELETE /api/vendor/services/{id}                 (→ INACTIVE, soft delete)
 * Plus the public `GET /api/categories` for the form's category dropdown.
 *
 * Gated two ways (mirrors /admin/vendors):
 *   - Client route guard: <RequireAuth requireRole="VENDOR"> redirects non-vendors home
 *     (and the unauthenticated to /login). Depends on the /me-in-rehydrate wiring so
 *     `user.role` survives a reload (a reloaded vendor would otherwise look role-less
 *     and get bounced).
 *   - Server-side: every endpoint requires a VENDOR-role JWT and returns 403 otherwise.
 *     The route guard is UX; the 403 is the actual security boundary.
 *
 * UI shape mirrors "My bookings" + the admin queue: loading skeleton → error card (with
 * retry) → empty state → list of rows + pagination. Each row exposes status-aware
 * actions (Edit always; Activate for DRAFT/INACTIVE; Deactivate for ACTIVE). The
 * "New service" button toggles an inline form (validated client-side to match the
 * backend constraints) that creates a DRAFT, after which the vendor can Activate to
 * publish. All mutations invalidate the vendor-services family so the list refetches.
 */
import { useEffect, useState } from "react";
import type { UseMutationResult } from "@tanstack/react-query";
import { RequireAuth } from "@/components/require-auth";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { ApiError } from "@/lib/api/client";
import { useCategories } from "@/lib/api/categories-queries";
import type { Category } from "@/lib/api/categories";
import {
  useActivateVendorService,
  useCreateVendorService,
  useDeactivateVendorService,
  useUpdateVendorService,
  useVendorServices,
} from "@/lib/api/vendor-services-queries";
import type {
  PricingType,
  ServiceCreateRequest,
  ServiceStatus,
  ServiceUpdateRequest,
  VendorService,
} from "@/lib/api/vendor-services";

const PAGE_SIZE = 10;

/**
 * The pricing models, mirrored from the backend enum. Shown as a `<select>` in the
 * form. The labels are human-friendly; the values are the exact enum strings the
 * backend expects (ServiceCreateRequest.pricingType is one of these).
 */
const PRICING_TYPES: Array<{ label: string; value: PricingType }> = [
  { label: "Fixed price", value: "FIXED" },
  { label: "Hourly", value: "HOURLY" },
  { label: "Variable (quote)", value: "VARIABLE" },
];

/**
 * Backend validation constraints, mirrored here for instant client-side feedback.
 * Matching the server avoids a wasted round-trip AND gives clearer per-field messages
 * than Spring's generic 400. These are the source of truth for the form's validation.
 */
const CONSTRAINTS = {
  titleMin: 5,
  titleMax: 200,
  descriptionMax: 2000,
  addressMax: 500,
  cityMax: 50,
} as const;

export default function VendorServicesPage() {
  return (
    <RequireAuth requireRole="VENDOR">
      <VendorServicesContent />
    </RequireAuth>
  );
}

function VendorServicesContent() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error, refetch, isFetching } =
    useVendorServices({ page, size: PAGE_SIZE });

  // Track which service has a status-change action in flight (only one row's button
  // spins at a time) and the per-row error so a 404/403/422 message shows next to the
  // right row, not globally. Mirrors the admin/vendors + bookings pages.
  const [actionServiceId, setActionServiceId] = useState<number | null>(null);
  const [errorServiceId, setErrorServiceId] = useState<number | null>(null);
  const [actionErrorMsg, setActionErrorMsg] = useState<string | null>(null);

  // The inline form: `null` = closed; `{ mode: "create" }` = new-service form open;
  // `{ mode: "edit", service }` = editing an existing row. Toggling opens/closes a
  // single form panel at the top of the list — simpler than per-row modals and keeps
  // the page compact. The form is the single source of truth for create/edit input.
  const [formState, setFormState] = useState<
    | null
    | { mode: "create" }
    | { mode: "edit"; service: VendorService }
  >(null);

  const activateMutation = useActivateVendorService();
  const deactivateMutation = useDeactivateVendorService();

  /**
   * Run a status-change mutation (activate OR deactivate) on a service. Typed against a
   * shared `UseMutationResult<unknown, unknown, number>` so BOTH mutations fit: activate
   * returns the updated ServiceResponse while deactivate resolves void (204). They share
   * the same `(id: number) => ...` input shape and the same success/error handling, so
   * one helper covers both. We ignore the mutation's `data` in the callbacks (we don't
   * need it — the list refetch carries the new status).
   */
  function runStatusAction(
    serviceId: number,
    mutation: UseMutationResult<unknown, unknown, number>,
    failureVerb: string,
  ): void {
    setActionServiceId(serviceId);
    setErrorServiceId(null);
    setActionErrorMsg(null);
    mutation.mutate(serviceId, {
      onSuccess: () => {
        setActionServiceId(null);
        // The hook invalidates the vendor-services family → the list refetches with the
        // new status. The row's action buttons re-render accordingly.
      },
      onError: (err: unknown) => {
        setActionServiceId(null);
        setErrorServiceId(serviceId);
        setActionErrorMsg(
          err instanceof ApiError
            ? err.message
            : `Couldn't ${failureVerb} this service.`,
        );
      },
    });
  }

  function handleActivate(serviceId: number) {
    if (!window.confirm("Publish this service? It will appear in the catalog.")) {
      return;
    }
    runStatusAction(serviceId, activateMutation, "activate");
  }

  function handleDeactivate(serviceId: number) {
    if (
      !window.confirm(
        "Deactivate this service? It will leave the catalog but stay in your list. You can reactivate it later.",
      )
    ) {
      return;
    }
    runStatusAction(serviceId, deactivateMutation, "deactivate");
  }

  const services = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">My services</h1>
          <p className="mt-1 text-neutral-600 dark:text-neutral-400">
            Create, edit, and publish the services you offer.
          </p>
        </div>
        {/*
          "New service" toggles the inline create form. Hidden while an edit form is
          open (the two share the single form slot) to avoid confusing UX.
        */}
        <button
          type="button"
          onClick={() => setFormState({ mode: "create" })}
          disabled={formState !== null}
          className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          + New service
        </button>
      </header>

      {/*
        The form panel. Rendered above the list so the vendor can create/edit without
        scrolling past rows. It's a controlled component seeded from the editing service
        (edit mode) or blank (create mode), and it closes itself on successful submit.
      */}
      {formState ? (
        <ServiceFormPanel
          key={
            formState.mode === "edit"
              ? `edit-${formState.service.id ?? "x"}`
              : "create"
          }
          mode={formState}
          onClose={() => setFormState(null)}
        />
      ) : null}

      {isPending ? (
        <CatalogSkeleton />
      ) : isError ? (
        <ErrorState
          error={error}
          onRetry={() => refetch()}
          title="Couldn't load your services."
        />
      ) : (
        <>
          {isFetching ? (
            <p className="mb-4 text-sm text-neutral-500">Refreshing…</p>
          ) : null}

          {services.length === 0 ? (
            <div className="rounded border border-dashed border-neutral-300 p-8 text-center dark:border-neutral-700">
              <p className="text-neutral-500 dark:text-neutral-400">
                You have no services yet. Create your first one to get started.
              </p>
            </div>
          ) : (
            <>
              <p className="mb-4 text-sm text-neutral-600 dark:text-neutral-400">
                {total} service{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-3">
                {services.map((service) => (
                  <ServiceRow
                    key={service.id ?? Math.random()}
                    service={service}
                    actionInFlight={actionServiceId === service.id}
                    actionError={
                      errorServiceId === service.id ? actionErrorMsg : null
                    }
                    onActivate={handleActivate}
                    onDeactivate={handleDeactivate}
                    onEdit={(svc) => setFormState({ mode: "edit", service: svc })}
                    // Disable Edit while another row's status action is mid-flight, to
                    // avoid editing a row whose status is about to change underneath us.
                    editDisabled={actionServiceId !== null}
                  />
                ))}
              </ul>
              <Pagination
                number={data?.number}
                totalPages={data?.totalPages}
                first={data?.first}
                last={data?.last}
                onPageChange={setPage}
                disabled={isFetching}
              />
            </>
          )}
        </>
      )}
    </main>
  );
}

/** Map a service status to a Tailwind color class for the badge. */
function statusBadgeClass(status: ServiceStatus): string {
  switch (status) {
    case "DRAFT":
      return "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300";
    case "ACTIVE":
      return "bg-green-100 text-green-800 dark:bg-green-950/50 dark:text-green-300";
    case "INACTIVE":
      return "bg-neutral-200 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-400";
    default:
      return "bg-neutral-100 text-neutral-700 dark:bg-neutral-900 dark:text-neutral-400";
  }
}

/** Format a price as currency. basePrice is a number (e.g. 12.5); currency is implicit USD. */
function formatPrice(amount: number | undefined): string {
  if (amount === undefined || amount === null) return "—";
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: "USD",
  }).format(amount);
}

interface ServiceRowProps {
  service: VendorService;
  actionInFlight: boolean;
  actionError: string | null;
  onActivate: (id: number) => void;
  onDeactivate: (id: number) => void;
  onEdit: (service: VendorService) => void;
  editDisabled: boolean;
}

function ServiceRow({
  service,
  actionInFlight,
  actionError,
  onActivate,
  onDeactivate,
  onEdit,
  editDisabled,
}: ServiceRowProps) {
  const status = service.status ?? "DRAFT";
  const serviceId = service.id ?? 0;
  // Activate only makes sense for non-published services; Deactivate only for live ones.
  // The backend would also reject/no-op the wrong transition, so gating here avoids a
  // pointless round-trip and keeps the action buttons meaningful per row.
  const canActivate = status === "DRAFT" || status === "INACTIVE";
  const canDeactivate = status === "ACTIVE";

  return (
    <li className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            {service.title || `Service #${service.id ?? "?"}`}
          </h3>
          {service.categoryName ? (
            <p className="text-sm text-neutral-500 dark:text-neutral-400">
              {service.categoryName}
            </p>
          ) : null}
        </div>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusBadgeClass(
            status,
          )}`}
        >
          {status}
        </span>
      </div>

      {service.description ? (
        <p className="mt-2 text-sm text-neutral-600 dark:text-neutral-400 line-clamp-2">
          {service.description}
        </p>
      ) : null}

      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            Price
          </dt>
          <dd>
            {formatPrice(service.basePrice)}
            {service.pricingType === "HOURLY" ? " /hr" : ""}
          </dd>
        </div>
        {service.durationHours ? (
          <div>
            <dt className="text-xs uppercase tracking-wide text-neutral-400">
              Duration
            </dt>
            <dd>{service.durationHours}h</dd>
          </div>
        ) : null}
        {service.city ? (
          <div>
            <dt className="text-xs uppercase tracking-wide text-neutral-400">
              City
            </dt>
            <dd>{service.city}</dd>
          </div>
        ) : null}
      </dl>

      {/*
        Actions row. Edit is always available (any status can be edited). Activate and
        Deactivate are mutually exclusive by status. All three are disabled while a
        status action is in flight for THIS row (per-row gate, matching admin/vendors).
      */}
      <div className="mt-3 flex flex-wrap gap-3">
        <button
          type="button"
          disabled={editDisabled || actionInFlight}
          onClick={() => onEdit(service)}
          className="rounded border border-neutral-300 px-3 py-1 text-sm text-neutral-700 hover:border-blue-400 hover:text-blue-600 disabled:opacity-50 dark:border-neutral-700 dark:text-neutral-300 dark:hover:text-blue-400"
        >
          Edit
        </button>
        {canActivate ? (
          <button
            type="button"
            disabled={actionInFlight}
            onClick={() => onActivate(serviceId)}
            className="rounded bg-green-600 px-3 py-1 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50 dark:bg-green-700 dark:hover:bg-green-600"
          >
            {actionInFlight ? "Working…" : "Activate"}
          </button>
        ) : null}
        {canDeactivate ? (
          <button
            type="button"
            disabled={actionInFlight}
            onClick={() => onDeactivate(serviceId)}
            className="rounded border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
          >
            Deactivate
          </button>
        ) : null}
      </div>

      {actionError ? (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400" role="alert">
          {actionError}
        </p>
      ) : null}
    </li>
  );
}

/* -------------------------------------------------------------------------- */
/* Service form panel (create + edit share this component)                    */
/* -------------------------------------------------------------------------- */

interface ServiceFormPanelProps {
  mode: { mode: "create" } | { mode: "edit"; service: VendorService };
  onClose: () => void;
}

/**
 * The shared create/edit form. Seeded from the editing service in edit mode (or blank
 * in create mode). Validates client-side against the backend constraints before
 * submitting; on success it closes itself (the parent's list refetches via the
 * mutation's invalidation).
 *
 * In edit mode the category dropdown is DISABLED: the backend's update request
 * (ServiceUpdateRequest) doesn't include categoryId, so re-categorizing isn't supported
 * — we show the current category read-only to set expectations.
 *
 * The `key` prop on the panel (set by the parent) forces a fresh mount when switching
 * between create/edit targets, so the form state reseeds cleanly each time.
 */
function ServiceFormPanel({ mode, onClose }: ServiceFormPanelProps) {
  const isEdit = mode.mode === "edit";
  const editing = isEdit ? mode.service : null;

  const { data: categories, isPending: categoriesPending } = useCategories();

  // Seed the form fields. Edit mode reads from the service; create mode starts blank.
  // We initialize useState ONCE (lazy initializer) so editing the same row doesn't
  // blow away in-progress edits on re-render — the `key` on the panel handles reseeding
  // when the target actually changes.
  const [categoryId, setCategoryId] = useState<string>(
    editing?.categoryId !== undefined ? String(editing.categoryId) : "",
  );
  const [title, setTitle] = useState(editing?.title ?? "");
  const [description, setDescription] = useState(editing?.description ?? "");
  const [pricingType, setPricingType] = useState<PricingType>(
    editing?.pricingType ?? "FIXED",
  );
  const [basePrice, setBasePrice] = useState<string>(
    editing?.basePrice !== undefined ? String(editing.basePrice) : "",
  );
  const [durationHours, setDurationHours] = useState<string>(
    editing?.durationHours !== undefined ? String(editing.durationHours) : "",
  );
  const [address, setAddress] = useState(editing?.address ?? "");
  const [city, setCity] = useState(editing?.city ?? "");
  const [imageUrl, setImageUrl] = useState(editing?.imageUrl ?? "");

  // Per-field validation errors + submit error. `touched` is deliberately simple: we
  // validate on submit and show all errors at once (no per-keystroke validation) to keep
  // the UX calm and the code small — matches the rest of the app's minimal form approach.
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const createMutation = useCreateVendorService();
  const updateMutation = useUpdateVendorService();
  const submitting = createMutation.isPending || updateMutation.isPending;

  /**
   * Validate the form against the backend constraints. Returns the (possibly empty)
   * errors map so the caller can both set state and decide whether to proceed.
   *
   * Why mirror the server rules client-side: instant, per-field feedback (Spring's 400
   * is generic) AND fewer wasted requests. The server is still the source of truth —
   * this is a UX layer, not a security boundary.
   */
  function validate(): Record<string, string> {
    const next: Record<string, string> = {};

    // categoryId is required on CREATE only (the update request omits it). In edit mode
    // the dropdown is disabled and we don't send categoryId at all.
    if (!isEdit) {
      if (!categoryId) next.categoryId = "Choose a category.";
    }

    const trimmedTitle = title.trim();
    if (trimmedTitle.length < CONSTRAINTS.titleMin) {
      next.title = `Title must be at least ${CONSTRAINTS.titleMin} characters.`;
    } else if (trimmedTitle.length > CONSTRAINTS.titleMax) {
      next.title = `Title must be at most ${CONSTRAINTS.titleMax} characters.`;
    }

    if (description.length > CONSTRAINTS.descriptionMax) {
      next.description = `Description must be at most ${CONSTRAINTS.descriptionMax} characters.`;
    }

    // basePrice is required and must be a positive number. Empty string or <= 0 fails.
    const priceNum = Number(basePrice);
    if (basePrice === "" || Number.isNaN(priceNum)) {
      next.basePrice = "Enter a price.";
    } else if (priceNum <= 0) {
      next.basePrice = "Price must be greater than 0.";
    }

    // durationHours is OPTIONAL, but if provided it must be a positive integer.
    if (durationHours !== "") {
      const dur = Number(durationHours);
      if (Number.isNaN(dur) || !Number.isInteger(dur) || dur <= 0) {
        next.durationHours = "Duration must be a positive whole number of hours.";
      }
    }

    if (address.length > CONSTRAINTS.addressMax) {
      next.address = `Address must be at most ${CONSTRAINTS.addressMax} characters.`;
    }
    if (city.length > CONSTRAINTS.cityMax) {
      next.city = `City must be at most ${CONSTRAINTS.cityMax} characters.`;
    }

    return next;
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitError(null);
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    // Build the request body. Number fields are coerced from strings (form inputs are
    // always strings); optionals are omitted when blank so the backend applies defaults.
    if (isEdit && editing) {
      const body: ServiceUpdateRequest = {
        title: title.trim(),
        description: description.trim() || undefined,
        pricingType,
        basePrice: Number(basePrice),
        // durationHours is optional — only send when the vendor provided one.
        durationHours:
          durationHours === "" ? undefined : Number(durationHours),
        address: address.trim() || undefined,
        city: city.trim() || undefined,
        imageUrl: imageUrl.trim() || undefined,
      };
      updateMutation.mutate(
        { id: editing.id ?? 0, body },
        {
          onSuccess: () => onClose(),
          onError: (err: unknown) => {
            setSubmitError(
              err instanceof ApiError
                ? err.message
                : "Couldn't save changes. Please try again.",
            );
          },
        },
      );
    } else {
      const body: ServiceCreateRequest = {
        categoryId: Number(categoryId),
        title: title.trim(),
        description: description.trim() || undefined,
        pricingType,
        basePrice: Number(basePrice),
        durationHours:
          durationHours === "" ? undefined : Number(durationHours),
        address: address.trim() || undefined,
        city: city.trim() || undefined,
        imageUrl: imageUrl.trim() || undefined,
      };
      createMutation.mutate(body, {
        onSuccess: () => onClose(),
        onError: (err: unknown) => {
          setSubmitError(
            err instanceof ApiError
              ? err.message
              : "Couldn't create the service. Please try again.",
          );
        },
      });
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-6 rounded-lg border border-neutral-300 p-4 dark:border-neutral-700"
      aria-label={isEdit ? "Edit service" : "Create service"}
    >
      <h2 className="mb-4 text-lg font-semibold">
        {isEdit ? "Edit service" : "New service"}
      </h2>

      {/*
        While categories load, the dropdown is disabled (we can't validate "choose a
        category" without the list). If the fetch fails, the dropdown renders empty and
        submit will surface the missing-category error — the categories endpoint is
        public and reliable, so a hard failure here is rare; we don't add a separate
        error card to keep the form compact.
      */}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Category"
          required={!isEdit}
          error={errors.categoryId}
        >
          <select
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            disabled={isEdit || categoriesPending}
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          >
            <option value="">
              {categoriesPending ? "Loading…" : "Choose…"}
            </option>
            {(categories ?? [])
              // Some rows may be missing an id/name in edge cases; guard the dropdown.
              .filter((c): c is Required<Category> => c.id !== undefined && !!c.name)
              .map((c) => (
                <option key={c.id} value={String(c.id)}>
                  {c.name}
                </option>
              ))}
          </select>
          {isEdit ? (
            <p className="mt-1 text-xs text-neutral-400">
              Category can&apos;t be changed after creation.
            </p>
          ) : null}
        </Field>

        <Field label="Title" required error={errors.title}>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={CONSTRAINTS.titleMax}
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>

        <Field label="Pricing type" required className="sm:col-span-2">
          <select
            value={pricingType}
            onChange={(e) => setPricingType(e.target.value as PricingType)}
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          >
            {PRICING_TYPES.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Base price (USD)" required error={errors.basePrice}>
          <input
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0"
            value={basePrice}
            onChange={(e) => setBasePrice(e.target.value)}
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>

        <Field label="Duration (hours)" error={errors.durationHours}>
          <input
            type="number"
            inputMode="numeric"
            step="1"
            min="1"
            value={durationHours}
            onChange={(e) => setDurationHours(e.target.value)}
            placeholder="optional"
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>

        <Field label="City" error={errors.city} className="sm:col-span-2">
          <input
            type="text"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            maxLength={CONSTRAINTS.cityMax}
            placeholder="optional"
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>

        <Field label="Address" error={errors.address} className="sm:col-span-2">
          <input
            type="text"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            maxLength={CONSTRAINTS.addressMax}
            placeholder="optional"
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>

        <Field
          label="Image URL"
          error={errors.imageUrl}
          className="sm:col-span-2"
        >
          <input
            type="url"
            value={imageUrl}
            onChange={(e) => setImageUrl(e.target.value)}
            placeholder="optional"
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>

        <Field
          label="Description"
          error={errors.description}
          className="sm:col-span-2"
        >
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={CONSTRAINTS.descriptionMax}
            rows={4}
            placeholder="optional"
            className="w-full rounded border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </Field>
      </div>

      {submitError ? (
        <p className="mt-4 text-sm text-red-600 dark:text-red-400" role="alert">
          {submitError}
        </p>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-3">
        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          {submitting
            ? "Saving…"
            : isEdit
              ? "Save changes"
              : "Create service"}
        </button>
        <button
          type="button"
          onClick={onClose}
          disabled={submitting}
          className="rounded border border-neutral-300 px-4 py-2 text-sm text-neutral-700 hover:border-blue-400 hover:text-blue-600 disabled:opacity-50 dark:border-neutral-700 dark:text-neutral-300 dark:hover:text-blue-400"
        >
          Cancel
        </button>
      </div>

      {/*
        Hint under the buttons: explain the create→activate flow so the vendor knows a
        brand-new service won't appear in the catalog until they hit Activate. Only shown
        in create mode (an edit can't change status).
      */}
      {!isEdit ? (
        <p className="mt-3 text-xs text-neutral-500 dark:text-neutral-400">
          New services are saved as drafts. Activate after creating to publish them.
        </p>
      ) : null}
    </form>
  );
}

/** A labelled form field wrapper. Keeps the grid tidy and the error pattern consistent. */
function Field({
  label,
  required,
  error,
  className,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  className?: string;
  children: React.ReactNode;
}) {
  // Reset the field's error visually when the message clears. We don't manage `touched`
  // here — the parent re-validates on submit and replaces the whole errors map, so a
  // corrected field simply has no entry next time. This effect is a no-op safety net for
  // aria-live announcements if we later switch to on-change validation.
  useEffect(() => {
    // intentionally empty: validation is submit-driven (see comment above).
  }, [error]);

  return (
    <label className={`block ${className ?? ""}`}>
      <span className="mb-1 block text-sm font-medium text-neutral-700 dark:text-neutral-300">
        {label}
        {required ? <span className="text-red-500"> *</span> : null}
      </span>
      {children}
      {error ? (
        <span className="mt-1 block text-xs text-red-600 dark:text-red-400">
          {error}
        </span>
      ) : null}
    </label>
  );
}
